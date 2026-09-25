package com.b2binventory.app.data

object ApiConfig {
    // For Android Emulator: use 10.0.2.2 (special alias to host machine's localhost)
    // For Physical Device: replace with your computer's IP address (e.g., 192.168.x.x)
    // Use ipconfig (Windows) or ifconfig (Mac/Linux) to find your IP
    const val BASE_URL = "http://10.0.2.2:8082/"
    
    // Alternative: If using physical device, uncomment and update with your IP:
    // const val BASE_URL = "http://YOUR_COMPUTER_IP:8082/"
}
