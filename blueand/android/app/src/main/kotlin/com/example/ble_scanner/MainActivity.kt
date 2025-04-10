package com.example.ble_scanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodCall

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.example.ble_scanner/ble"
    private lateinit var methodChannel: MethodChannel

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    private val bleScanner by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private var scanning = false
    private val scanResults = mutableListOf<ScanResult>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "checkPermissions" -> {
                    checkPermissions(result)
                }
                "isBluetoothEnabled" -> {
                    result.success(isBluetoothEnabled)
                }
                "enableBluetooth" -> {
                    enableBluetooth(result)
                }
                "startBleScan" -> {
                    startBleScan(result)
                }
                "stopBleScan" -> {
                    stopBleScan(result)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun checkPermissions(result: MethodChannel.Result) {
        val permissionsToRequest = mutableListOf<String>()

        // Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // For Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissionsToRequest.isEmpty()) {
            // All permissions already granted
            result.success(true)
            return
        }
        
        // Store the result to respond after permission request
        permissionResult = result
        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    private val PERMISSION_REQUEST_CODE = 1001
    private var permissionResult: MethodChannel.Result? = null

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check if all permissions were granted
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            permissionResult?.success(allGranted)
            permissionResult = null
        }
    }

    private val ENABLE_BT_REQUEST_CODE = 1002
    private var bluetoothEnableResult: MethodChannel.Result? = null

    private fun enableBluetooth(result: MethodChannel.Result) {
        if (isBluetoothEnabled) {
            result.success(true)
            return
        }

        try {
            bluetoothEnableResult = result
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BT_REQUEST_CODE)
        } catch (e: Exception) {
            result.error("ENABLE_BT_FAILED", e.message, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == ENABLE_BT_REQUEST_CODE) {
            bluetoothEnableResult?.success(isBluetoothEnabled)
            bluetoothEnableResult = null
        }
    }

    private fun startBleScan(result: MethodChannel.Result) {
        if (scanning) {
            result.error("ALREADY_SCANNING", "Scanning is already in progress", null)
            return
        }

        // Check permissions before scanning
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                result.error("PERMISSION_DENIED", "Missing BLUETOOTH_SCAN permission", null)
                return
            }
        } else {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                result.error("PERMISSION_DENIED", "Missing ACCESS_FINE_LOCATION permission", null)
                return
            }
        }

        if (!isBluetoothEnabled) {
            result.error("BLUETOOTH_DISABLED", "Bluetooth is not enabled", null)
            return
        }

        try {
            scanResults.clear()
            bleScanner?.startScan(scanCallback)
            scanning = true
            result.success(true)
        } catch (e: Exception) {
            result.error("SCAN_FAILED", e.message, null)
        }
    }

    private fun stopBleScan(result: MethodChannel.Result) {
        if (!scanning) {
            result.success(false)
            return
        }

        try {
            // Check permissions before stopping scan
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                    bleScanner?.stopScan(scanCallback)
                    scanning = false
                    result.success(true)
                } else {
                    result.error("PERMISSION_DENIED", "Missing BLUETOOTH_SCAN permission", null)
                }
            } else {
                bleScanner?.stopScan(scanCallback)
                scanning = false
                result.success(true)
            }
        } catch (e: Exception) {
            result.error("STOP_SCAN_FAILED", e.message, null)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceIndex = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (deviceIndex >= 0) {
                scanResults[deviceIndex] = result
            } else {
                scanResults.add(result)
            }

            // Safe device name access with permission check
            val deviceName = try {
                result.device.name ?: "Unnamed Device"
            } catch (e: SecurityException) {
                "Unnamed Device" // Fallback if permission not granted
            }
            
            val deviceInfo = mapOf(
                "name" to deviceName,
                "address" to result.device.address,
                "rssi" to result.rssi
            )
            
            mainHandler.post {
                methodChannel.invokeMethod("onScanResult", deviceInfo)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            mainHandler.post {
                scanning = false
                methodChannel.invokeMethod("onScanFailed", "Scan failed with error: $errorCode")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (scanning) {
            bleScanner?.stopScan(scanCallback)
            scanning = false
        }
    }
}
