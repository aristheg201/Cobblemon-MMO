# Bộ class 0.2.4 — Fabric 1.21.1 / Java 21

Bản class giao trước, gồm client, bảy JAR server và cấu hình. World boss được giao riêng sau.

## Thay đổi

- Stormbreaker: ném tối đa 20 block, bấm lại để thu; quét mọi mục tiêu hợp lệ sát đường bay, mỗi lượt một hit. Không xuyên vật cản, không tạo bản sao item, tự dọn khi đổi item/class hoặc mất phiên chơi.
- Batman: bộ giáp đen/graphite mới, không Beyond/Classic; cape luôn thay cape cá nhân và hỗ trợ liệng. Móc `fantasyhub:batman_grappling_hook` kéo entity/player hoặc bám địa hình; giữ chuột phải để rút dây, Nhảy/Cúi để thả. Batclaw kéo tới gần rồi gây sát thương/choáng. Quy tắc PvP và miễn khống chế vẫn áp dụng.
- Tăng VFX/animation cho các hero, thêm vệt chuyển động Flash. Menu có bộ lọc Hero và tìm kiếm Flash. Sửa lỗi đệ quy gây crash khi mở/tìm trong menu class.
- Mỗi nhân vật mới chọn một class miễn phí. Class khác cần Class Sigil; kỹ năng cần đủ cấp và điểm học trước khi dùng. Lượt đầu bỏ qua phí/hồi chuyển class; các lần sau theo cấu hình.
- EXP từ thắng trận Pokémon và hạ Pokémon ngoài thế giới tăng cấp class đang dùng; chống cộng lặp. Pokémon hoang chủ động đuổi và đánh, tránh đánh chồng với Fight or Flight. Pokémon sở hữu/đang bắt/đang đấu/đang bận được bảo vệ.
- Giữ sửa lỗi startup GeckoLib/AzureLib, model Mjolnir từ Soulslike đã cung cấp, hướng lưỡi Stormbreaker, item cầm thật, đồ RPG không mất độ bền, cape và điều kiện bay Thor/Strange.

## Cài đặt

1. Thay FantasyHubClient cũ bằng JAR trong `client/mods`. EMF/ETF nằm riêng, không nhúng vào client. Bật pack **SVFrame · Detailed Animations**.
2. Thay đủ bảy JAR trong `server/mods`; Bridge thay Core/Bridge cũ có cùng mod ID `fantasyhubcore`. SVFrameLib/MMO giữ số phiên bản cũ nhưng phải dùng đúng file trong bộ này. Singleplayer cần bộ server trong instance chơi đơn.
3. Giữ Fabric API, Polymer và Cobblemon **1.8.2** cùng các dependency của pack. Bộ này không đóng gói Cobblemon/Fight or Flight/Better Combat. Native QA có Fight or Flight 0.11.1, Better Combat 2.4.0, Accessories/Trinkets, EMF/ETF, GeckoLib/AzureLib.
4. Merge `config/` nếu đã chỉnh class/item; dùng nguyên bộ mặc định nếu muốn áp dụng các rule học kỹ năng mới. File mẫu `config-merge/SVFrameMMOCobblemon/pokemon-gameplay.json` là phần JSON cần merge vào `config/SVFrameMMOCobblemon/config.json`; mod tự sinh các giá trị mặc định khi thiếu.
5. Mở `/classes`, chọn bộ lọc Hero. Chọn class, học kỹ năng trong hub, gán vào một trong bảy ô. Thor có tám kỹ năng để chọn/gán; ném rìu cần cầm Stormbreaker thật.

`config/FantasyHub/progression.json` chỉnh giá mở class (mặc định 60), điểm học và phí/hồi đổi class. Sigil là nguyên liệu **lưu trong dữ liệu nhân vật**, không phải paper đổi tên; không mất khi túi đầy. World boss cấp nguyên liệu được giao sau; quản trị có thể cấp để thử bằng `/fantasyprogress sigils <player> <amount>`.

Thor: class + set chest/legs/boots Thor + Thor Cape trong Back; Strange: Sorcerer Supreme + Cloak of Levitation trong Back. Nhấn đúp Nhảy để bay. Thor/Strange ưu tiên cape cá nhân; cape Batman luôn được hiển thị.

## Xác nhận và giới hạn

200 JUnit tests của các module class/framework/integration và native core/integration smoke đã qua. Đã thử native ném/thu qua 12 mục tiêu, giới hạn 20 block, kính mỏng, đổi item, móc bằng chuột/phím thật, kéo player/PvP, học skill/mở class và EXP/hostile Pokémon. Có video và ảnh native cho đủ 29 kỹ năng Thor/Strange/Spider/Batman ở hai góc, cùng các lượt kiểm tra bổ sung cuối và Flash.

Báo cáo QA phân biệt bản baseline với bản render cuối; không xem “cast accepted” là bằng chứng VFX. Video QA không có âm thanh. Chưa chạy nguyên bộ 289 mod Windows của người dùng. Hiệu ứng được thích nghi cho Minecraft, không sao chép nguyên cinematic của video reference.
