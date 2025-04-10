import 'package:flutter/material.dart';
import '../models/ble_device.dart';

class DeviceItem extends StatelessWidget {
  final BleDevice device;

  const DeviceItem({super.key, required this.device});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 4.0),
      elevation: 2.0,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              device.name,
              style: const TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16.0,
              ),
            ),
            const SizedBox(height: 4),
            Text('Address: ${device.address}'),
            const SizedBox(height: 4),
            Text('Signal Strength: ${device.rssi} dBm'),
          ],
        ),
      ),
    );
  }
}
