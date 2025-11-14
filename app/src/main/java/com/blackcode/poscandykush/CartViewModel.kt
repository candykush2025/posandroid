package com.blackcode.poscandykush
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class CartViewModel : ViewModel() {

    private val apiService = CartApiService()

    private val _cartState = MutableStateFlow<CartResponse?>(null)
    val cartState: StateFlow<CartResponse?> = _cartState

    private val _paymentState = MutableStateFlow<PaymentStatusResponse?>(null)
    val paymentState: StateFlow<PaymentStatusResponse?> = _paymentState

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun startPollingCart() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val response = apiService.getCart()
                    if (response != null) {
                        _cartState.emit(response)
                        if (response.success) {
                            _errorMessage.emit(null)
                        } else {
                            _errorMessage.emit(response.error ?: "Unknown error from API")
                        }
                    } else {
                        _errorMessage.emit("No response from server")
                    }
                    _isLoading.emit(false)
                } catch (e: Exception) {
                    android.util.Log.e("CartViewModel", "Exception in polling cart", e)
                    _errorMessage.emit("Network error: ${e.message}")
                    _isLoading.emit(false)
                }
                delay(2000) // Poll every 2 seconds
            }
        }
    }

    fun startPollingPaymentStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val response = apiService.getPaymentStatus()
                    _paymentState.emit(response)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(2000)
            }
        }
    }
}