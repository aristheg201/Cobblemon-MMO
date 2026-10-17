BRIDGE 0.1.10 — SỬA KUNAI GUN

Server: gỡ FantasyHubBridge-0.1.9.jar, thay bằng FantasyHubBridge-0.1.10.jar. Giữ 5 mod SVFrameLib/MMO/Items/Mobs/Integration rời đang dùng. Không cài Core. Khởi động lại server.
Client: giữ FantasyHubClient-0.1.9-helmetfix1.jar đã sửa mũ; không cần đổi client.

Đang dùng class Kabuto, đeo Zecter Belt và cầm Kabuto Kunai Gun ở tay chính:
- Chuột phải: bắn trong Gun Mode; đạn đi theo camera, bắn được vào không khí hoặc tường.
- Shift + chuột phải hoặc skill Kunai Mode: đổi Gun/Kunai.
- Kunai Mode: cận chiến; không bắn đạn.
Cooldown, stamina, class, belt và quy tắc mục tiêu vẫn do server xác thực. Bắn hụt không cộng Zecter Energy/Clock Gauge.

Nếu config class đang gộp nhầm hai file: SVFrameMMO/classes/kabuto.yml phải có các skill KABUTO_* và level/max-level; cooldown/cân bằng đặt ở FantasyHub/kabuto.yml. Không đặt HENSHIN: {cooldown:...} vào phần skills của file class.

Bản vá này không tuyên bố hoàn thành toàn bộ RPG/MythicMobs/Skript port.
