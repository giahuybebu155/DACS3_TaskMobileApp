package com.example.taskapplication.ui.team.task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskapplication.data.util.DataStoreManager
import com.example.taskapplication.domain.model.TeamTask
import com.example.taskapplication.domain.repository.TeamRepository
import com.example.taskapplication.domain.repository.TeamTaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Team Tasks screen
 * Manages the state and data for displaying team tasks
 */
@HiltViewModel
class TeamTaskViewModel @Inject constructor(
    private val teamTaskRepository: TeamTaskRepository,
    private val teamRepository: TeamRepository,
    private val dataStoreManager: DataStoreManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    // Team ID from navigation arguments
    private val teamId: String = checkNotNull(savedStateHandle.get<String>("teamId"))
    
    // State for team tasks
    private val _tasksState = MutableStateFlow<TeamTasksState>(TeamTasksState.Loading)
    val tasksState: StateFlow<TeamTasksState> = _tasksState
    
    // State for create task operation
    private val _createTaskState = MutableStateFlow<CreateTaskState>(CreateTaskState.Idle)
    val createTaskState: StateFlow<CreateTaskState> = _createTaskState
    
    // State for showing create task dialog
    private val _showCreateTaskDialog = MutableStateFlow(false)
    val showCreateTaskDialog: StateFlow<Boolean> = _showCreateTaskDialog
    
    // State for team members (for assigning tasks)
    private val _teamMembersState = MutableStateFlow<TeamMembersState>(TeamMembersState.Loading)
    val teamMembersState: StateFlow<TeamMembersState> = _teamMembersState
    
    init {
        loadTasks()
        loadTeamMembers()
    }
    
    /**
     * Load tasks for the team
     */
    fun loadTasks() {
        viewModelScope.launch {
            _tasksState.value = TeamTasksState.Loading
            
            teamTaskRepository.getTasksByTeam(teamId)
                .catch { e ->
                    _tasksState.value = TeamTasksState.Error(e.message ?: "Unknown error")
                }
                .collect { tasks ->
                    _tasksState.value = if (tasks.isEmpty()) {
                        TeamTasksState.Empty
                    } else {
                        TeamTasksState.Success(tasks)
                    }
                }
        }
    }
    
    /**
     * Load team members for task assignment
     */
    private fun loadTeamMembers() {
        viewModelScope.launch {
            _teamMembersState.value = TeamMembersState.Loading
            
            teamRepository.getTeamMembers(teamId)
                .catch { e ->
                    _teamMembersState.value = TeamMembersState.Error(e.message ?: "Unknown error")
                }
                .collect { members ->
                    _teamMembersState.value = if (members.isEmpty()) {
                        TeamMembersState.Empty
                    } else {
                        TeamMembersState.Success(members)
                    }
                }
        }
    }
    
    /**
     * Show the create task dialog
     */
    fun showCreateTaskDialog() {
        _showCreateTaskDialog.value = true
    }
    
    /**
     * Hide the create task dialog
     */
    fun hideCreateTaskDialog() {
        _showCreateTaskDialog.value = false
        // Reset create task state
        _createTaskState.value = CreateTaskState.Idle
    }
    
    /**
     * Create a new task
     */
    fun createTask(title: String, description: String?, dueDate: Long?, priority: Int, assignedUserId: String?) {
        viewModelScope.launch {
            _createTaskState.value = CreateTaskState.Loading
            
            try {
                val userId = dataStoreManager.getCurrentUserId()
                if (userId != null) {
                    val newTask = TeamTask(
                        id = "",  // Repository will generate ID
                        teamId = teamId,
                        title = title,
                        description = description,
                        dueDate = dueDate,
                        priority = priority,
                        isCompleted = false,
                        assignedUserId = assignedUserId
                    )
                    
                    teamTaskRepository.createTask(newTask)
                        .onSuccess {
                            _createTaskState.value = CreateTaskState.Success
                            hideCreateTaskDialog()
                            loadTasks()
                        }
                        .onFailure { e ->
                            _createTaskState.value = CreateTaskState.Error(e.message ?: "Failed to create task")
                        }
                } else {
                    _createTaskState.value = CreateTaskState.Error("User not logged in")
                }
            } catch (e: Exception) {
                _createTaskState.value = CreateTaskState.Error(e.message ?: "Failed to create task")
            }
        }
    }
    
    /**
     * Toggle task completion status
     */
    fun toggleTaskCompletion(task: TeamTask) {
        viewModelScope.launch {
            val updatedTask = task.copy(isCompleted = !task.isCompleted)
            teamTaskRepository.updateTask(updatedTask)
                .onFailure { e ->
                    // Handle error if needed
                }
        }
    }
    
    /**
     * Reset error state
     */
    fun resetError() {
        if (_tasksState.value is TeamTasksState.Error) {
            _tasksState.value = TeamTasksState.Loading
            loadTasks()
        }
        
        if (_createTaskState.value is CreateTaskState.Error) {
            _createTaskState.value = CreateTaskState.Idle
        }
    }
}

/**
 * State for team tasks
 */
sealed class TeamTasksState {
    object Loading : TeamTasksState()
    object Empty : TeamTasksState()
    data class Success(val tasks: List<TeamTask>) : TeamTasksState()
    data class Error(val message: String) : TeamTasksState()
}

/**
 * State for create task operation
 */
sealed class CreateTaskState {
    object Idle : CreateTaskState()
    object Loading : CreateTaskState()
    object Success : CreateTaskState()
    data class Error(val message: String) : CreateTaskState()
}

/**
 * State for team members
 */
sealed class TeamMembersState {
    object Loading : TeamMembersState()
    object Empty : TeamMembersState()
    data class Success(val members: List<com.example.taskapplication.domain.model.TeamMember>) : TeamMembersState()
    data class Error(val message: String) : TeamMembersState()
}
