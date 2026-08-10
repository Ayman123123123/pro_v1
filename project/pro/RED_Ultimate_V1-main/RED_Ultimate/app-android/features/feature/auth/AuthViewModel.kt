package com.red.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.red.core.models.UserStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Pending : AuthUiState()
    object Authenticated : AuthUiState()
    object Rejected : AuthUiState()
    object Banned : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authApi: AuthApi
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = authApi.register(mapOf("name" to name, "email" to email, "password" to password))
                if (response.isSuccessful) {
                    _uiState.value = AuthUiState.Pending
                } else {
                    _uiState.value = AuthUiState.Error("Registration failed: ${response.message()}")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val response = authApi.login(mapOf("email" to email, "password" to password))
                if (response.isSuccessful) {
                    val user = response.body()?.user
                    when (user?.status) {
                        UserStatus.APPROVED -> _uiState.value = AuthUiState.Authenticated
                        UserStatus.PENDING -> _uiState.value = AuthUiState.Pending
                        UserStatus.REJECTED -> _uiState.value = AuthUiState.Rejected
                        UserStatus.BANNED -> _uiState.value = AuthUiState.Banned
                        else -> _uiState.value = AuthUiState.Error("Unknown user status")
                    }
                } else {
                    _uiState.value = AuthUiState.Error("Login failed")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun checkStatus() {
        viewModelScope.launch {
            try {
                val response = authApi.getStatus()
                if (response.isSuccessful) {
                    when (response.body()?.status) {
                        UserStatus.APPROVED -> _uiState.value = AuthUiState.Authenticated
                        UserStatus.PENDING -> _uiState.value = AuthUiState.Pending
                        UserStatus.REJECTED -> _uiState.value = AuthUiState.Rejected
                        UserStatus.BANNED -> _uiState.value = AuthUiState.Banned
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                // Ignore status check errors
            }
        }
    }
}
