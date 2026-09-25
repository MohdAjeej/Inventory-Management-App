package com.b2binventory.app.data

object ApiConfig {
    // For Android Emulator: use 10.0.2.2 (special alias to host machine's localhost)
    // For Physical Device: replace with your computer's IP address (e.g., 192.168.x.x)
    // Use ipconfig (Windows) or ifconfig (Mac/Linux) to find your IP
    
    // Physical Device Configuration - Using your computer's IP
    const val BASE_URL = "http://192.168.29.166:8082/"
    
    // For Emulator, use this instead:
    // const val BASE_URL = "http://10.0.2.2:8082/"
}
