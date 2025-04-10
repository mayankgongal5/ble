import 'dart:async';
import 'dart:developer' as dev;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../models/ble_device.dart';
import '../widgets/device_item.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  // IMPORTANT: Make sure this channel name exactly matches the one in Kotlin code
  static const platform = MethodChannel('com.example.ble_scanner/ble');

  bool _scanning = false;
  final List<BleDevice> _scanResults = [];
  bool _hasError = false;
  String _errorMessage = "";

  @override
  void initState() {
    super.initState();
    dev.log('Setting up method call handler');
    platform.setMethodCallHandler(_handleMethodCall);

    // Check platform channel connection on startup
    _checkPlatformChannelConnection();
  }

  Future<void> _checkPlatformChannelConnection() async {
    try {
      dev.log('Testing platform channel connection');
      // Try a simple method call to test if the platform channel is connected
      await platform.invokeMethod('isBluetoothEnabled');
      dev.log('Platform channel is connected');
    } catch (e) {
      dev.log('Platform channel error: $e');
      setState(() {
        _hasError = true;
        _errorMessage = 'Failed to connect to native platform: $e';
      });
    }
  }

  Future<dynamic> _handleMethodCall(MethodCall call) async {
    dev.log('Received method call: ${call.method}');

    switch (call.method) {
      case 'onScanResult':
        final Map<dynamic, dynamic> device =
            Map<dynamic, dynamic>.from(call.arguments);
        dev.log('Scan result received: $device');

        final bleDevice = BleDevice(
          name: device['name'] ?? 'Unnamed Device',
          address: device['address'] ?? '',
          rssi: device['rssi'] ?? 0,
        );

        setState(() {
          final existingIndex =
              _scanResults.indexWhere((d) => d.address == bleDevice.address);

          if (existingIndex >= 0) {
            _scanResults[existingIndex] = bleDevice;
          } else {
            _scanResults.add(bleDevice);
          }
        });
        break;

      case 'onScanFailed':
        dev.log('Scan failed: ${call.arguments}');
        setState(() {
          _scanning = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${call.arguments}')),
        );
        break;
    }
    return null;
  }

  Future<void> _startScan() async {
    try {
      dev.log('Starting scan process');
      // Check permissions
      final bool? hasPermissions =
          await platform.invokeMethod('checkPermissions');
      dev.log('Permission check result: $hasPermissions');

      if (hasPermissions != true) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Bluetooth permissions are required')),
        );
        return;
      }

      // Check if Bluetooth is enabled
      final bool? bluetoothEnabled =
          await platform.invokeMethod('isBluetoothEnabled');
      dev.log('Bluetooth enabled: $bluetoothEnabled');

      if (bluetoothEnabled != true) {
        // Try to enable Bluetooth
        final bool? enabled = await platform.invokeMethod('enableBluetooth');
        dev.log('Bluetooth enable result: $enabled');

        if (enabled != true) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Bluetooth is required for scanning')),
          );
          return;
        }
      }

      // Clear previous scan results
      setState(() {
        _scanResults.clear();
      });

      // Start scanning
      dev.log('Invoking startBleScan method');
      final bool? success = await platform.invokeMethod('startBleScan');
      dev.log('Start scan result: $success');

      if (success == true) {
        setState(() {
          _scanning = true;
        });
      }
    } catch (e) {
      dev.log('Error in _startScan: $e');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error: ${e.toString()}')),
      );
    }
  }

  Future<void> _stopScan() async {
    if (!_scanning) return;

    try {
      dev.log('Stopping scan');
      await platform.invokeMethod('stopBleScan');
      setState(() {
        _scanning = false;
      });
    } catch (e) {
      dev.log('Error stopping scan: $e');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error stopping scan: ${e.toString()}')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_hasError) {
      return Scaffold(
        appBar: AppBar(
          title: const Text('BLE Scanner Error'),
          centerTitle: true,
          backgroundColor: Theme.of(context).colorScheme.error,
          foregroundColor: Theme.of(context).colorScheme.onError,
        ),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 50, color: Colors.red),
                const SizedBox(height: 16),
                Text(
                  'Platform Connection Error',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                const SizedBox(height: 8),
                Text(
                  _errorMessage,
                  style: Theme.of(context).textTheme.bodyMedium,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: _checkPlatformChannelConnection,
                  child: const Text('Try Again'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    // Normal app UI when no error
    return Scaffold(
      appBar: AppBar(
        title: const Text('BLE Scanner'),
        centerTitle: true,
        backgroundColor: Theme.of(context).colorScheme.primaryContainer,
        foregroundColor: Theme.of(context).colorScheme.onPrimaryContainer,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            if (_scanning)
              Column(
                children: [
                  LinearProgressIndicator(
                    color: Theme.of(context).colorScheme.primary,
                    backgroundColor:
                        Theme.of(context).colorScheme.surfaceVariant,
                  ),
                  const SizedBox(height: 16),
                ],
              ),
            if (_scanResults.isEmpty)
              Expanded(
                child: Center(
                  child: Text(
                    _scanning ? 'Scanning...' : 'No devices found',
                    style: Theme.of(context).textTheme.bodyLarge,
                  ),
                ),
              )
            else
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: const EdgeInsets.only(bottom: 8.0),
                      child: Text(
                        'Found ${_scanResults.length} devices:',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                    Expanded(
                      child: ListView.builder(
                        itemCount: _scanResults.length,
                        itemBuilder: (context, index) {
                          return DeviceItem(device: _scanResults[index]);
                        },
                      ),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          if (_scanning) {
            _stopScan();
          } else {
            _startScan();
          }
        },
        child: Text(_scanning ? 'Stop' : 'Scan'),
      ),
    );
  }
}
