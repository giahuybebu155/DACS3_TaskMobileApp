package com.example.taskapplication.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskapplication.data.network.NetworkConnectivityObserver
import com.example.taskapplication.data.sync.SyncEvent
import com.example.taskapplication.data.sync.SyncManager
import com.example.taskapplication.util.DataStoreManager
import com.example.taskapplication.data.websocket.ChatEvent
import com.example.taskapplication.data.websocket.ConnectionState
import com.example.taskapplication.data.websocket.WebSocketManager
import com.example.taskapplication.domain.model.Team
import com.example.taskapplication.domain.model.TeamInvitation
import com.example.taskapplication.domain.model.TeamMessage
import com.example.taskapplication.domain.model.TeamTask
import com.example.taskapplication.domain.repository.TeamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para gerenciar a sincronização e conexão WebSocket
 */
@HiltViewModel
class MainSyncViewModel @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val syncManager: SyncManager,
    private val teamRepository: TeamRepository,
    private val networkConnectivityObserver: NetworkConnectivityObserver,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val TAG = "MainSyncViewModel"

    // Estado da conexão WebSocket
    private val _webSocketState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val webSocketState: StateFlow<ConnectionState> = _webSocketState.asStateFlow()

    // Estado da conexão de rede
    private val _networkAvailable = MutableStateFlow(false)
    val networkAvailable: StateFlow<Boolean> = _networkAvailable.asStateFlow()

    // Estado da sincronização
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    // Último timestamp de sincronização
    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    // Notificações de eventos
    private val _notifications = MutableStateFlow<List<SyncNotification>>(emptyList())
    val notifications: StateFlow<List<SyncNotification>> = _notifications.asStateFlow()

    init {
        // Observar estado da conexão de rede
        viewModelScope.launch {
            networkConnectivityObserver.observe().collectLatest { isAvailable ->
                _networkAvailable.value = isAvailable

                if (isAvailable) {
                    // Quando a rede estiver disponível, conectar WebSocket e sincronizar
                    connectWebSocket()
                    syncData()
                }
            }
        }

        // Observar estado da conexão WebSocket
        viewModelScope.launch {
            webSocketManager.connectionState.collectLatest { state ->
                _webSocketState.value = state

                if (state == ConnectionState.CONNECTED) {
                    // Quando WebSocket estiver conectado, inscrever-se nos canais de equipe
                    subscribeToTeams()
                }
            }
        }

        // Observar eventos WebSocket
        viewModelScope.launch {
            webSocketManager.events.collectLatest { event ->
                handleWebSocketEvent(event)
            }
        }

        // Observar eventos de sincronização
        viewModelScope.launch {
            syncManager.syncEvents.collectLatest { event ->
                handleSyncEvent(event)
            }
        }

        // Carregar último timestamp de sincronização
        viewModelScope.launch {
            _lastSyncTime.value = dataStoreManager.getLastSyncTimestamp()
        }
    }

    /**
     * Conectar ao WebSocket
     */
    private suspend fun connectWebSocket() {
        try {
            val authToken = dataStoreManager.getAuthToken()
            val currentTeamId = dataStoreManager.getCurrentTeamId()

            if (authToken != null && currentTeamId != null) {
                Log.d(TAG, "Conectando ao WebSocket com token=${authToken.take(5)}... e teamId=$currentTeamId")
                webSocketManager.connect(authToken, currentTeamId)
            } else {
                Log.e(TAG, "Não foi possível conectar ao WebSocket: token ou teamId ausente")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao conectar ao WebSocket", e)
        }
    }

    /**
     * Inscrever-se nos canais de equipe
     */
    private suspend fun subscribeToTeams() {
        try {
            val teams = syncManager.getLocalTeams()
            Log.d(TAG, "Inscrevendo-se em ${teams.size} canais de equipe")

            teams.forEach { team ->
                if (team.serverId != null) {
                    webSocketManager.subscribeToTeam(team.serverId.toLong())
                    Log.d(TAG, "Inscrito no canal da equipe ${team.name} (${team.serverId})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inscrever-se nos canais de equipe", e)
        }
    }

    /**
     * Sincronizar dados
     */
    fun syncData() {
        if (!_networkAvailable.value) {
            Log.d(TAG, "Não é possível sincronizar: sem conexão de rede")
            return
        }

        if (_syncState.value == SyncState.Syncing) {
            Log.d(TAG, "Sincronização já em andamento")
            return
        }

        viewModelScope.launch {
            _syncState.value = SyncState.Syncing

            try {
                val result = syncManager.syncAll()

                if (result.isSuccess) {
                    _syncState.value = SyncState.Success
                    _lastSyncTime.value = System.currentTimeMillis()
                    dataStoreManager.saveLastSyncTimestamp(_lastSyncTime.value!!)
                    Log.d(TAG, "Sincronização concluída com sucesso")
                } else {
                    _syncState.value = SyncState.Error(result.exceptionOrNull()?.message ?: "Erro desconhecido")
                    Log.e(TAG, "Erro na sincronização: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(e.message ?: "Erro desconhecido")
                Log.e(TAG, "Exceção durante a sincronização", e)
            }
        }
    }

    /**
     * Manipular evento WebSocket
     */
    private fun handleWebSocketEvent(event: ChatEvent) {
        viewModelScope.launch {
            when (event) {
                is ChatEvent.TeamInvitation -> {
                    Log.d(TAG, "Recebido evento de convite para equipe: ${event.teamId}")
                    addNotification(
                        SyncNotification(
                            id = "invitation_${event.invitationId}",
                            title = "Novo convite para equipe",
                            message = "Você foi convidado para uma equipe",
                            type = NotificationType.INVITATION
                        )
                    )
                }
                is ChatEvent.NewMessage -> {
                    Log.d(TAG, "Recebido evento de nova mensagem: ${event.message.id}")
                    // Sincronizar mensagens
                    syncManager.syncTeamMessages(event.message.teamId?.toLong())
                }
                is ChatEvent.TeamUpdated -> {
                    Log.d(TAG, "Recebido evento de atualização de equipe: ${event.teamId}")
                    // Sincronizar equipes
                    syncManager.syncTeams()
                }
                is ChatEvent.TeamMemberAdded -> {
                    Log.d(TAG, "Recebido evento de adição de membro: ${event.userId} na equipe ${event.teamId}")
                    // Sincronizar membros da equipe
                    syncManager.syncTeamMembers(event.teamId.toLong())
                }
                is ChatEvent.TeamMemberRemoved -> {
                    Log.d(TAG, "Recebido evento de remoção de membro: ${event.userId} da equipe ${event.teamId}")
                    // Sincronizar membros da equipe
                    syncManager.syncTeamMembers(event.teamId.toLong())
                }
                else -> {
                    // Ignorar outros eventos
                }
            }
        }
    }

    /**
     * Manipular evento de sincronização
     */
    private fun handleSyncEvent(event: SyncEvent) {
        viewModelScope.launch {
            when (event) {
                is SyncEvent.SyncCompleted -> {
                    _syncState.value = SyncState.Success
                    _lastSyncTime.value = System.currentTimeMillis()
                }
                is SyncEvent.SyncFailed -> {
                    _syncState.value = SyncState.Error(event.error)
                }
                is SyncEvent.NewInvitation -> {
                    addNotification(
                        SyncNotification(
                            id = "invitation_${event.invitation.id}",
                            title = "Novo convite para equipe",
                            message = "Você foi convidado para a equipe ${event.invitation.teamName}",
                            type = NotificationType.INVITATION
                        )
                    )
                }
                is SyncEvent.NewTask -> {
                    addNotification(
                        SyncNotification(
                            id = "task_${event.task.id}",
                            title = "Nova tarefa",
                            message = "Uma nova tarefa foi atribuída a você: ${event.task.title}",
                            type = NotificationType.TASK
                        )
                    )
                }
                is SyncEvent.NewMessage -> {
                    addNotification(
                        SyncNotification(
                            id = "message_${event.message.id}",
                            title = "Nova mensagem",
                            message = "Você recebeu uma nova mensagem",
                            type = NotificationType.MESSAGE
                        )
                    )
                }
                else -> {
                    // Ignorar outros eventos
                }
            }
        }
    }

    /**
     * Adicionar notificação
     */
    private fun addNotification(notification: SyncNotification) {
        val currentNotifications = _notifications.value.toMutableList()
        // Verificar se já existe uma notificação com o mesmo ID
        if (currentNotifications.none { it.id == notification.id }) {
            currentNotifications.add(notification)
            _notifications.value = currentNotifications
        }
    }

    /**
     * Remover notificação
     */
    fun removeNotification(notificationId: String) {
        val currentNotifications = _notifications.value.toMutableList()
        currentNotifications.removeAll { it.id == notificationId }
        _notifications.value = currentNotifications
    }

    /**
     * Limpar todas as notificações
     */
    fun clearNotifications() {
        _notifications.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        webSocketManager.disconnect()
    }
}

/**
 * Estado de sincronização
 */
sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    object Success : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * Notificação de sincronização
 */
data class SyncNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: NotificationType,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Tipo de notificação
 */
enum class NotificationType {
    INVITATION,
    TASK,
    MESSAGE,
    TEAM_UPDATE
}
