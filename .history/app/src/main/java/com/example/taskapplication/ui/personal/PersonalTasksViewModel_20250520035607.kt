package com.example.taskapplication.ui.personal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taskapplication.domain.model.PersonalTask
import com.example.taskapplication.domain.repository.PersonalTaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import retrofit2.Response
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class PersonalTasksViewModel @Inject constructor(
    private val personalTaskRepository: PersonalTaskRepository
) : ViewModel() {

    private val _tasks = MutableStateFlow<TasksState>(TasksState.Loading)
    val tasks: StateFlow<TasksState> = _tasks

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _showAddDialog = MutableStateFlow(false)
    val showAddDialog: StateFlow<Boolean> = _showAddDialog

    private val _showFilterDialog = MutableStateFlow(false)
    val showFilterDialog: StateFlow<Boolean> = _showFilterDialog

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _filterStatus = MutableStateFlow<String?>(null)
    val filterStatus: StateFlow<String?> = _filterStatus

    private val _filterPriority = MutableStateFlow<String?>(null)
    val filterPriority: StateFlow<String?> = _filterPriority

    private val _filterDueDateStart = MutableStateFlow<Long?>(null)
    val filterDueDateStart: StateFlow<Long?> = _filterDueDateStart

    private val _filterDueDateEnd = MutableStateFlow<Long?>(null)
    val filterDueDateEnd: StateFlow<Long?> = _filterDueDateEnd

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages

    private val _hasMorePages = MutableStateFlow(false)
    val hasMorePages: StateFlow<Boolean> = _hasMorePages

    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount

    private val perPage = 20

    init {
        loadTasks()

        // Theo dõi số lượng công việc đang chờ đồng bộ
        viewModelScope.launch {
            personalTaskRepository.countPendingSyncTasks()
                .collect { count ->
                    _pendingSyncCount.value = count
                }
        }
    }

    fun loadTasks() {
        viewModelScope.launch {
            _tasks.value = TasksState.Loading

            // Nếu có bộ lọc hoặc tìm kiếm, sử dụng API lọc và tìm kiếm
            if (_filterStatus.value != null || _filterPriority.value != null ||
                _filterDueDateStart.value != null || _filterDueDateEnd.value != null ||
                _searchQuery.value.isNotEmpty()) {

                loadFilteredTasks()
            } else {
                // Nếu không có bộ lọc, tải tất cả công việc từ local
                personalTaskRepository.getAllTasks()
                    .catch { e ->
                        _tasks.value = TasksState.Error(e.message ?: "Unknown error")
                    }
                    .collect { tasks ->
                        _tasks.value = TasksState.Success(tasks)
                    }
            }
        }
    }

    private suspend fun loadFilteredTasks() {
        try {
            _tasks.value = TasksState.Loading

            try {
                // Thử tải dữ liệu từ server trước
                val result = personalTaskRepository.filterAndSearchTasksFromServer(
                    status = _filterStatus.value,
                    priority = _filterPriority.value,
                    startDate = _filterDueDateStart.value,
                    endDate = _filterDueDateEnd.value,
                    query = if (_searchQuery.value.isNotEmpty()) _searchQuery.value else null,
                    page = _currentPage.value,
                    perPage = perPage
                )

                if (result.isSuccess) {
                    val (tasks, meta) = result.getOrThrow()
                    _tasks.value = TasksState.Success(tasks)

                    // Cập nhật thông tin phân trang
                    _totalPages.value = meta?.lastPage ?: 1
                    _hasMorePages.value = meta != null && meta.currentPage < meta.lastPage
                    return
                } else {
                    val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"

                    // Nếu lỗi không phải do kết nối mạng, hiển thị lỗi
                    if (!errorMessage.contains("No network connection") &&
                        !errorMessage.contains("Lỗi kết nối")) {

                        val userFriendlyMessage = when {
                            errorMessage.contains("Lỗi kết nối đến máy chủ") -> errorMessage
                            else -> "Đã xảy ra lỗi khi tải dữ liệu. Vui lòng thử lại sau."
                        }

                        _tasks.value = TasksState.Error(userFriendlyMessage)
                        Log.e("PersonalTasksViewModel", "Error loading tasks from server: $errorMessage")
                        return
                    }

                    // Nếu lỗi do kết nối mạng, tiếp tục với dữ liệu cục bộ
                    Log.i("PersonalTasksViewModel", "Network error, falling back to local data")
                }
            } catch (e: IOException) {
                // Lỗi kết nối mạng, tiếp tục với dữ liệu cục bộ
                Log.i("PersonalTasksViewModel", "Network error, falling back to local data", e)
            }

            // Sử dụng dữ liệu cục bộ khi không có kết nối mạng
            personalTaskRepository.filterAndSearchTasksLocally(
                status = _filterStatus.value,
                priority = _filterPriority.value,
                startDate = _filterDueDateStart.value,
                endDate = _filterDueDateEnd.value,
                query = if (_searchQuery.value.isNotEmpty()) _searchQuery.value else null
            ).collect { tasks ->
                _tasks.value = TasksState.Success(tasks)

                // Không có phân trang khi sử dụng dữ liệu cục bộ
                _hasMorePages.value = false
                _totalPages.value = 1

                // Hiển thị thông báo đang sử dụng dữ liệu cục bộ
                Log.i("PersonalTasksViewModel", "Using local data: ${tasks.size} tasks")
            }
        } catch (e: Exception) {
            val userFriendlyMessage = when (e) {
                is IOException -> "Không có kết nối mạng. Đang hiển thị dữ liệu cục bộ."
                else -> "Đã xảy ra lỗi không xác định. Vui lòng thử lại sau."
            }
            _tasks.value = TasksState.Error(userFriendlyMessage)
            Log.e("PersonalTasksViewModel", "Exception loading tasks", e)
        }
    }

    fun loadNextPage() {
        if (_hasMorePages.value) {
            viewModelScope.launch {
                try {
                    _currentPage.value += 1

                    try {
                        val result = personalTaskRepository.filterAndSearchTasksFromServer(
                            status = _filterStatus.value,
                            priority = _filterPriority.value,
                            startDate = _filterDueDateStart.value,
                            endDate = _filterDueDateEnd.value,
                            query = if (_searchQuery.value.isNotEmpty()) _searchQuery.value else null,
                            page = _currentPage.value,
                            perPage = perPage
                        )

                        if (result.isSuccess) {
                            val (newTasks, meta) = result.getOrThrow()

                            // Thêm các công việc mới vào danh sách hiện tại
                            val currentTasks = (_tasks.value as? TasksState.Success)?.tasks ?: emptyList()
                            _tasks.value = TasksState.Success(currentTasks + newTasks)

                            // Cập nhật thông tin phân trang
                            _totalPages.value = meta?.lastPage ?: 1
                            _hasMorePages.value = meta != null && meta.currentPage < meta.lastPage
                        } else {
                            // Nếu có lỗi, giảm lại trang hiện tại
                            _currentPage.value = (_currentPage.value - 1).coerceAtLeast(1)

                            val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"

                            // Nếu lỗi do kết nối mạng, hiển thị thông báo thân thiện
                            if (errorMessage.contains("No network connection") ||
                                errorMessage.contains("Lỗi kết nối")) {

                                // Hiển thị thông báo đang sử dụng dữ liệu cục bộ
                                Log.i("PersonalTasksViewModel", "Network error when loading next page, using local data only")

                                // Đặt hasMorePages thành false vì không thể tải thêm khi offline
                                _hasMorePages.value = false
                            } else {
                                Log.e("PersonalTasksViewModel", "Error loading next page: $errorMessage")
                            }
                        }
                    } catch (e: IOException) {
                        // Nếu có lỗi kết nối mạng, giảm lại trang hiện tại
                        _currentPage.value = (_currentPage.value - 1).coerceAtLeast(1)

                        // Đặt hasMorePages thành false vì không thể tải thêm khi offline
                        _hasMorePages.value = false

                        Log.i("PersonalTasksViewModel", "Network error when loading next page, using local data only", e)
                    }
                } catch (e: Exception) {
                    // Nếu có lỗi, giảm lại trang hiện tại
                    _currentPage.value = (_currentPage.value - 1).coerceAtLeast(1)
                    Log.e("PersonalTasksViewModel", "Exception loading next page", e)
                }
            }
        }
    }

    // Trạng thái đồng bộ
    sealed class SyncState {
        object None : SyncState()
        object Success : SyncState()
        data class Error(val message: String) : SyncState()
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.None)
    val syncState: StateFlow<SyncState> = _syncState

    fun syncTasks() {
        viewModelScope.launch {
            Log.d("PersonalTasksViewModel", "Starting sync process")
            _isRefreshing.value = true
            _syncState.value = SyncState.None

            try {
                // Bọc toàn bộ quá trình đồng bộ trong try-catch để đảm bảo không có lỗi nào làm crash ứng dụng
                try {
                    val result = personalTaskRepository.syncTasks()

                    if (result.isSuccess) {
                        // Sau khi đồng bộ, tải lại danh sách công việc
                        try {
                            resetFiltersAndLoadTasks()
                        } catch (e: Exception) {
                            Log.e("PersonalTasksViewModel", "Error reloading tasks after sync", e)
                        }

                        _syncState.value = SyncState.Success

                        // Tự động ẩn thông báo thành công sau 3 giây
                        try {
                            delay(3000)
                            _syncState.value = SyncState.None
                        } catch (e: Exception) {
                            Log.e("PersonalTasksViewModel", "Error hiding success message", e)
                        }
                    } else {
                        val exception = result.exceptionOrNull()
                        val errorMessage = exception?.message ?: "Lỗi không xác định"

                        // Xử lý lỗi JSON parsing
                        val userFriendlyMessage = when {
                            exception is IllegalStateException &&
                            (errorMessage.contains("Expected BEGIN_OBJECT but was BEGIN_ARRAY") ||
                             errorMessage.contains("BEGIN_ARRAY") ||
                             errorMessage.contains("JsonObject")) -> {
                                Log.e("PersonalTasksViewModel", "JSON parsing error during sync", exception)
                                "Đồng bộ một phần thành công. Một số dữ liệu có thể chưa được đồng bộ đầy đủ."
                            }
                            exception is IOException -> "Lỗi kết nối mạng. Vui lòng kiểm tra kết nối và thử lại."
                            else -> "Đã xảy ra lỗi khi đồng bộ: $errorMessage"
                        }

                        _syncState.value = SyncState.Error(userFriendlyMessage)

                        // Tự động ẩn thông báo lỗi sau 5 giây
                        try {
                            delay(5000)
                            _syncState.value = SyncState.None
                        } catch (e: Exception) {
                            Log.e("PersonalTasksViewModel", "Error hiding error message", e)
                        }

                        // Nếu là lỗi JSON parsing, vẫn tải lại dữ liệu cục bộ
                        if (exception is IllegalStateException &&
                            (errorMessage.contains("Expected BEGIN_OBJECT but was BEGIN_ARRAY") ||
                             errorMessage.contains("BEGIN_ARRAY") ||
                             errorMessage.contains("JsonObject"))) {
                            try {
                                resetFiltersAndLoadTasks()
                            } catch (e: Exception) {
                                Log.e("PersonalTasksViewModel", "Error reloading tasks after JSON parsing error", e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PersonalTasksViewModel", "Error syncing tasks", e)

                    val userFriendlyMessage = when {
                        e is IllegalStateException &&
                        (e.message?.contains("Expected BEGIN_OBJECT but was BEGIN_ARRAY") == true ||
                         e.message?.contains("BEGIN_ARRAY") == true ||
                         e.message?.contains("JsonObject") == true) -> {
                            "Đồng bộ một phần thành công. Một số dữ liệu có thể chưa được đồng bộ đầy đủ."
                        }
                        e is IOException -> "Lỗi kết nối mạng. Vui lòng kiểm tra kết nối và thử lại."
                        else -> "Đã xảy ra lỗi khi đồng bộ: ${e.message ?: "Lỗi không xác định"}"
                    }

                    _syncState.value = SyncState.Error(userFriendlyMessage)

                    // Tự động ẩn thông báo lỗi sau 5 giây
                    try {
                        delay(5000)
                        _syncState.value = SyncState.None
                    } catch (e: Exception) {
                        Log.e("PersonalTasksViewModel", "Error hiding error message", e)
                    }

                    // Nếu là lỗi JSON parsing, vẫn tải lại dữ liệu cục bộ
                    if (e is IllegalStateException &&
                        (e.message?.contains("Expected BEGIN_OBJECT but was BEGIN_ARRAY") == true ||
                         e.message?.contains("BEGIN_ARRAY") == true ||
                         e.message?.contains("JsonObject") == true)) {
                        try {
                            resetFiltersAndLoadTasks()
                        } catch (e2: Exception) {
                            Log.e("PersonalTasksViewModel", "Error reloading tasks after JSON parsing error", e2)
                        }
                    }
                }
            } catch (e: Throwable) {
                // Bắt tất cả các lỗi có thể xảy ra, kể cả Error
                Log.e("PersonalTasksViewModel", "Critical error during sync", e)

                try {
                    _syncState.value = SyncState.Error("Đã xảy ra lỗi nghiêm trọng khi đồng bộ. Vui lòng thử lại sau.")

                    // Tự động ẩn thông báo lỗi sau 5 giây
                    delay(5000)
                    _syncState.value = SyncState.None
                } catch (e2: Exception) {
                    Log.e("PersonalTasksViewModel", "Error handling critical error", e2)
                }
            } finally {
                try {
                    _isRefreshing.value = false
                } catch (e: Exception) {
                    Log.e("PersonalTasksViewModel", "Error setting refreshing state to false", e)
                }
            }
        }
    }

    fun searchTasks(query: String) {
        _searchQuery.value = query
        _currentPage.value = 1
        loadTasks()
    }

    fun filterTasks(status: String?, priority: String?, dueDateStart: Long?, dueDateEnd: Long?) {
        _filterStatus.value = status
        _filterPriority.value = priority
        _filterDueDateStart.value = dueDateStart
        _filterDueDateEnd.value = dueDateEnd
        _currentPage.value = 1
        loadTasks()
    }

    fun clearFilters() {
        _filterStatus.value = null
        _filterPriority.value = null
        _filterDueDateStart.value = null
        _filterDueDateEnd.value = null
        _searchQuery.value = ""
        _currentPage.value = 1
        loadTasks()
    }

    private fun resetFiltersAndLoadTasks() {
        clearFilters()
        loadTasks()
    }

    fun showFilterDialog() {
        _showFilterDialog.value = true
    }

    fun hideFilterDialog() {
        _showFilterDialog.value = false
    }

    fun toggleTaskCompletion(task: PersonalTask) {
        viewModelScope.launch {
            val updatedTask = task.copy(
                status = if (task.status == "completed") "pending" else "completed",
                syncStatus = "pending_update",
                lastModified = System.currentTimeMillis()
            )
            personalTaskRepository.updateTask(updatedTask)
        }
    }

    fun createTask(task: PersonalTask) {
        viewModelScope.launch {
            // Lấy order lớn nhất hiện tại và tăng lên 1
            val currentTasks = (_tasks.value as? TasksState.Success)?.tasks ?: emptyList()
            val maxOrder = currentTasks.maxOfOrNull { it.order } ?: 0

            val taskWithOrder = task.copy(
                order = maxOrder + 1,
                syncStatus = "pending_create",
                lastModified = System.currentTimeMillis()
            )
            personalTaskRepository.createTask(taskWithOrder)
            hideAddTaskDialog()
        }
    }

    fun updateTask(task: PersonalTask) {
        viewModelScope.launch {
            personalTaskRepository.updateTask(task)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            personalTaskRepository.deleteTask(taskId)
        }
    }

    fun showAddTaskDialog() {
        _showAddDialog.value = true
    }

    fun hideAddTaskDialog() {
        _showAddDialog.value = false
    }

    /**
     * Hiển thị thông báo rằng chức năng đồng bộ đã bị vô hiệu hóa tạm thời
     */
    // Không cần phương thức này nữa vì chúng ta sẽ sửa lỗi đồng bộ

    sealed class TasksState {
        object Loading : TasksState()
        data class Success(val tasks: List<PersonalTask>) : TasksState()
        data class Error(val message: String) : TasksState()
    }
}
