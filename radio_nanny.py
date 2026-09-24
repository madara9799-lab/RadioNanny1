import asyncio
import random
import time
from pathlib import Path
import urllib.request

class RadioNanny:
    def __init__(self):
        self.station_url = "https://stream.radioexample.com/live"
        self.is_playing = False
        self.last_sent = 0
        self.cooldown = 300
        self.camera_active = False
    
    async def start(self):
        self.is_playing = True
        print(f"▶️ Radio nanny started: {self.station_url}")
        await self.monitor_loop()
    
    async def stop(self):
        self.is_playing = False
        self.camera_active = False
        print("⏹️ Radio nanny stopped")
    
    async def monitor_loop(self):
        while self.is_playing:
            try:
                await self.check_stream()
                await asyncio.sleep(60)
            except Exception as e:
                print(f"Error: {e}")
                await asyncio.sleep(30)
    
    async def check_stream(self):
        is_alive = random.choice([True, False])
        
        if is_alive and not self.is_playing:
            self.is_playing = True
            await self.send_telegram("📻 Radio nanny is now LIVE!")
        elif not is_alive and self.is_playing:
            self.is_playing = False
            await self.send_telegram("⚠️ Radio nanny stream is OFFLINE")
    
    async def send_telegram(self, message: str):
        current_time = time.time()
        if current_time - self.last_sent < self.cooldown:
            return
        
        # Try to capture camera frame
        if "КАМЕРА" in message.upper():
            await self.capture_and_send_photo()
        
        print(f"📱 Telegram: {message}")
        self.last_sent = current_time
    
    async def capture_and_send_photo(self):
        """Capture photo from camera and send via Telegram"""
        try:
            # For demonstration - simulate photo capture
            # In real app would use picamera or similar
            timestamp = int(time.time())
            photo_path = f"camera_{timestamp}.jpg"
            
            # Simulate capturing
            with open(photo_path, "w") as f:
                f.write("simulated_photo_data")
            
            file_size = os.path.getsize(photo_path) / 1024  # KB
            await self.send_telegram(f"📸 Фото с камеры ({file_size:.1f} KB)")
            
            # Clean up
            try:
                os.remove(photo_path)
            except:
                pass
        except Exception as e:
            await self.send_telegram(f"❌ Ошибка камеры: {str(e)[:50]}")
    
    async def send_apk_via_telegram(self, apk_path: Path):
        """Send APK file via Telegram"""
        if not apk_path.exists():
            print("❌ APK file not found")
            return
        
        file_size = apk_path.stat().st_size / 1024 / 1024  # MB
        if file_size > 50:
            print(f"⚠️ APK too large ({file_size:.1f}MB > 50MB limit)")
            return
        
        print(f"📤 Sending APK via Telegram: {apk_path.name} ({file_size:.1f}MB)")
        await self.send_telegram(f"📲 New Radio Nanny APK available! ({file_size:.1f}MB)")


async def main():
    nanny = RadioNanny()
    await nanny.start()
    await asyncio.sleep(3)
    await nanny.stop()
    
    # Simulate APK send
    apk_file = Path("radio_nanny-release.apk")
    if apk_file.exists():
        await nanny.send_apk_via_telegram(apk_file)

if __name__ == "__main__":
    asyncio.run(main())