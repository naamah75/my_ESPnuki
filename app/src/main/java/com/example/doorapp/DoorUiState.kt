package com.example.doorapp

data class DoorUiState(
  val isBusy: Boolean = false,
  val status: String = "Idle",
  val challenge: String = "",
  val debug: String = "",
  val lastResponse: String = "",
  val log: String = "",
  val scanResults: List<String> = emptyList(),
)
