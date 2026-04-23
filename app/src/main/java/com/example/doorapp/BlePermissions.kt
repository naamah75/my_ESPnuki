package com.example.doorapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BlePermissions {
  fun required(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
      Manifest.permission.BLUETOOTH_SCAN,
      Manifest.permission.BLUETOOTH_CONNECT,
    )
  } else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
  }

  fun missing(context: Context): Array<String> = required().filterNot {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
  }.toTypedArray()

  fun hasAll(context: Context): Boolean = missing(context).isEmpty()
}
