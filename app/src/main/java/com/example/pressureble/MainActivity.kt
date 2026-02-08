package com.example.pressureble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pressureble.databinding.ActivityMainBinding
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    // BLE
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isConnected = false
    private var isScanning = false
    
    // UUIDs matching ESP32 firmware
    private val SERVICE_UUID = UUID.fromString("0000181A-0000-1000-8000-00805F9B34FB")
    private val CHARACTERISTIC_UUID = UUID.fromString("12345678-1234-5678-1234-56789ABCDEF0")
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    
    // Data
    private val readings = mutableListOf<PressureReading>()
    private lateinit var adapter: ReadingsAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    // Permissions
    private val PERMISSION_REQUEST_CODE = 1
    
    data class PressureReading(val value: Float, val timestamp: Date)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupRecyclerView()
        setupBluetooth()
        setupButtons()
        
        checkPermissions()
    }
    
    private fun setupRecyclerView() {
        adapter = ReadingsAdapter(readings)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    private fun setupBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    }
    
    private fun setupButtons() {
        binding.btnScan.setOnClickListener {
            if (isConnected) {
                disconnect()
            } else if (!isScanning) {
                startScan()
            }
        }
        
        binding.btnClear.setOnClickListener {
            readings.clear()
            adapter.notifyDataSetChanged()
            binding.tvCurrentValue.text = "--%"
        }
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (bluetoothAdapter?.isEnabled != true) {
            Toast.makeText(this, "Attiva il Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }
        
        isScanning = true
        updateUI()
        binding.tvStatus.text = "Scansione in corso..."
        
        val scanFilter = ScanFilter.Builder()
            .setDeviceName("ESP32_Pressure")
            .build()
            
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        bluetoothLeScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
        
        // Timeout scan after 15 seconds
        handler.postDelayed({
            if (isScanning && !isConnected) {
                stopScan()
                binding.tvStatus.text = "ESP32_Pressure non trovato"
            }
        }, 15000)
    }
    
    @SuppressLint("MissingPermission")
    private fun stopScan() {
        isScanning = false
        bluetoothLeScanner?.stopScan(scanCallback)
        updateUI()
    }
    
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScan()
            binding.tvStatus.text = "Connessione a ${result.device.name}..."
            connectToDevice(result.device)
        }
        
        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            binding.tvStatus.text = "Errore scan: $errorCode"
            updateUI()
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }
    
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        isConnected = true
                        binding.tvStatus.text = "Connesso! Ricerca servizi..."
                        gatt.discoverServices()
                        updateUI()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        isConnected = false
                        binding.tvStatus.text = "Disconnesso"
                        updateUI()
                    }
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                
                if (characteristic != null) {
                    // Enable notifications
                    gatt.setCharacteristicNotification(characteristic, true)
                    
                    val descriptor = characteristic.getDescriptor(CCCD_UUID)
                    descriptor?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                        } else {
                            @Suppress("DEPRECATION")
                            it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            @Suppress("DEPRECATION")
                            gatt.writeDescriptor(it)
                        }
                    }
                    
                    handler.post {
                        binding.tvStatus.text = "Ricezione dati attiva!"
                    }
                } else {
                    handler.post {
                        binding.tvStatus.text = "Caratteristica non trovata"
                    }
                }
            }
        }
        
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleCharacteristicChanged(characteristic.value)
        }
        
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleCharacteristicChanged(value)
        }
        
        private fun handleCharacteristicChanged(data: ByteArray?) {
            if (data != null && data.size >= 4) {
                // Decode float (Little Endian)
                val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                val pressure = buffer.float
                
                handler.post {
                    val reading = PressureReading(pressure, Date())
                    readings.add(0, reading)
                    
                    // Limit to 100 readings
                    if (readings.size > 100) {
                        readings.removeAt(readings.size - 1)
                    }
                    
                    adapter.notifyDataSetChanged()
                    binding.tvCurrentValue.text = String.format("%.1f%%", pressure)
                    
                    // Update color based on value
                    val color = when {
                        pressure < 20 -> ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_light)
                        pressure < 50 -> ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_light)
                        pressure < 80 -> ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark)
                        else -> ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_light)
                    }
                    binding.tvCurrentValue.setTextColor(color)
                }
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected = false
        updateUI()
        binding.tvStatus.text = "Disconnesso"
    }
    
    private fun updateUI() {
        binding.btnScan.text = when {
            isConnected -> "Disconnetti"
            isScanning -> "Scansione..."
            else -> "Scan"
        }
        binding.btnScan.isEnabled = !isScanning
        
        binding.statusCard.setCardBackgroundColor(
            if (isConnected) 
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            else 
                ContextCompat.getColor(this, android.R.color.holo_blue_dark)
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
    
    // RecyclerView Adapter
    inner class ReadingsAdapter(private val items: List<PressureReading>) : 
        RecyclerView.Adapter<ReadingsAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvValue: TextView = view.findViewById(R.id.tvValue)
            val tvTime: TextView = view.findViewById(R.id.tvTime)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_reading, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvValue.text = String.format("%.1f%%", item.value)
            holder.tvTime.text = timeFormat.format(item.timestamp)
            
            val color = when {
                item.value < 20 -> ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_light)
                item.value < 50 -> ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_light)
                item.value < 80 -> ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark)
                else -> ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_light)
            }
            holder.tvValue.setTextColor(color)
        }
        
        override fun getItemCount() = items.size
    }
}
