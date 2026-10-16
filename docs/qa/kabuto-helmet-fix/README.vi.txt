Kabuto helmet face fix — Client 0.1.9 helmetfix1

Chỉ thay JAR CLIENT: gỡ FantasyHubClient-0.1.9.jar cũ trong mods của máy chơi, thay bằng FantasyHubClient-0.1.9-helmetfix1.jar. Không giữ cả hai. Khởi động lại game.
Giữ Bridge 0.1.9 và các JAR server hiện tại. Không cài JAR client lên dedicated server.
Phiên bản bên trong JAR vẫn là 0.1.9 để đúng handshake Bridge hiện tại. Đây là bản vá hiển thị, không đổi networking/assets/config.

Sửa: ẩn cả ModelPart đầu và lớp ngoài skin khi server xác nhận player đang Henshin/Masked, Rider sau Cast Off hoặc Hyper. Mũ Kabuto vẫn render theo góc quay đầu. Khi trở về người, renderer vanilla tự khôi phục đầu/skin ở lượt render tiếp theo.

Chỉ KabutoVisuals.class khác với client 0.1.9 đã giao. Không có thay đổi gameplay, item hay config. Xem bằng chứng QA riêng kèm theo.
