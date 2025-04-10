package com.example.blueand

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
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val TAG = "BleScanner"
    
    // IMPORTANT: Make sure this CHANNEL name exactly matches the one in Flutter code
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
    
    private val PERMISSION_REQUEST_CODE = 1001
    private var permissionResult: MethodChannel.Result? = null
    
    private val ENABLE_BT_REQUEST_CODE = 1002
    private var bluetoothEnableResult: MethodChannel.Result? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        Log.d(TAG, "Configuring Flutter Engine and setting up method channel")
        
        methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        methodChannel.setMethodCallHandler { call, result ->
            Log.d(TAG, "Method call received: ${call.method}")
            
            try {
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
            } catch (e: Exception) {
                Log.e(TAG, "Error handling method call: ${e.message}", e)
                result.error("NATIVE_ERROR", e.message ?: "Unknown error occurred", e.toString())
            }
        }
    }

    private fun checkPermissions(result: MethodChannel.Result) {
        Log.d(TAG, "Checking permissions")
        
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
            Log.d(TAG, "All permissions already granted")
            result.success(true)
            return
        }
        
        // Store the result to respond after permission request
        Log.d(TAG, "Requesting permissions: $permissionsToRequest")
        permissionResult = result
        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check if all permissions were granted
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            Log.d(TAG, "Permission request result: $allGranted")
            
            permissionResult?.success(allGranted)
            permissionResult = null
        }
    }

    private fun enableBluetooth(result: MethodChannel.Result) {
        Log.d(TAG, "Attempting to enable Bluetooth")
        
        if (isBluetoothEnabled) {
            Log.d(TAG, "Bluetooth is already enabled")
            result.success(true)
            return
        }

        try {
            bluetoothEnableResult = result
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BT_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling Bluetooth: ${e.message}", e)
            result.error("ENABLE_BT_FAILED", e.message, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == ENABLE_BT_REQUEST_CODE) {
            val enabled = isBluetoothEnabled
            Log.d(TAG, "Bluetooth enable result: $enabled")
            
            bluetoothEnableResult?.success(enabled)
            bluetoothEnableResult = null
        }
    }

    private fun startBleScan(result: MethodChannel.Result) {
        Log.d(TAG, "Starting BLE scan")
        
        if (scanning) {
            Log.d(TAG, "Scan already in progress")
            result.error("ALREADY_SCANNING", "Scanning is already in progress", null)
            return
        }

        // Check if Bluetooth is enabled
        if (!isBluetoothEnabled) {
            Log.d(TAG, "Bluetooth is not enabled")
            result.error("BLUETOOTH_DISABLED", "Bluetooth is not enabled", null)
            return
        }
        
        // Check if scanner is available
        if (bleScanner == null) {
            Log.e(TAG, "BLE Scanner is not available")
            result.error("SCANNER_UNAVAILABLE", "Bluetooth LE Scanner is not available", null)
            return
        }

        try {
            Log.d(TAG, "Starting scan")
            scanResults.clear()
            bleScanner?.startScan(scanCallback)
            scanning = true
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan: ${e.message}", e)
            result.error("SCAN_FAILED", e.message, e.toString())
        }
    }

    private fun stopBleScan(result: MethodChannel.Result) {
        Log.d(TAG, "Stopping BLE scan")
        
        if (!scanning) {
            Log.d(TAG, "No scan in progress")
            result.success(false)
            return
        }

        try {
            Log.d(TAG, "Stopping scanner")
            bleScanner?.stopScan(scanCallback)
            scanning = false
            result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}", e)
            result.error("STOP_SCAN_FAILED", e.message, e.toString())
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceIndex = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (deviceIndex >= 0) {
                scanResults[deviceIndex] = result
            } else {
                scanResults.add(result)
                Log.d(TAG, "New device found: ${result.device.address}")
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
                try {
                    Log.d(TAG, "Sending scan result to Flutter: $deviceInfo")
                    methodChannel.invokeMethod("onScanResult", deviceInfo)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending scan result to Flutter: ${e.message}", e)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            
            mainHandler.post {
                try {
                    scanning = false
                    methodChannel.invokeMethod("onScanFailed", "Scan failed with error: $errorCode")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending scan failure to Flutter: ${e.message}", e)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (scanning) {
            Log.d(TAG, "Activity pausing, stopping scan")
            bleScanner?.stopScan(scanCallback)
            scanning = false
        }
    }
}
