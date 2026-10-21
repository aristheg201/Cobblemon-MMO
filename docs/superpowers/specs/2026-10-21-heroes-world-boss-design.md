# Hero combat, class progression và world boss

Trạng thái: **bản thiết kế để người dùng duyệt; chưa triển khai**.
Ngày: 2026-10-21. Nền hiện tại: `da595ff`, FantasyHub 0.2.3, Fabric 1.21.1 / Java 21.

## Mục tiêu và phạm vi

Đáp ứng đủ 11 yêu cầu mới: Stormbreaker ném/thu; suit Batman khác; VFX Batman/Flash rõ; item grappling hook; QA chi tiết bốn hero; EXP Pokémon tăng level class; mở khóa trước khi dùng skill; Pokémon chủ động đánh player; side mod world boss theo lịch có hai loại boss; nguyên liệu mở class; mỗi player chọn một class đầu tiên miễn phí.

Giữ các yêu cầu đã chốt: lưỡi Stormbreaker hướng xuống khi cầm; không hiện vũ khí chỉ vì chọn Thor; cape Batman luôn đè cape skin và hỗ trợ glide; Thor/Strange ưu tiên cape cá nhân; điều kiện bay vẫn theo class/trang bị; đồ RPG không mất độ bền; Detailed Animations hoạt động cùng skill poses/Better Combat. Giao đủ client, server, config và bằng chứng QA thật.

**Đã được người dùng xác nhận:** Stormbreaker bay xa tối đa **20 block**, chỉ đánh các mục tiêu sát đường rìu; không phải AoE bán kính 20 block.

## Khảo sát code và nguyên nhân đã xác định

- `HeroClassFiles.generate` hiện sinh cả 7 skill mỗi hero với `unlocked-by-default: true`, cùng yêu cầu level 1. Đây là lý do chính sách mặc định không yêu cầu học skill.
- `HeroRuntime.prepare` kiểm tra class/CC/target, nhưng chưa kiểm tra quyền học skill ở đường gọi native provider. Cần cùng một điều kiện server ở mọi đường cast, không chỉ tắt nút GUI.
- `HeroCatalog` bắt buộc đúng 7 skill, slot 1–7. Thor cần **skill thứ 8**, nên phải mở ràng buộc catalog và cho người chơi bind bộ skill đã học; không thay thế Mjolnir bằng Stormbreaker.
- `ThorSkills.MJOLNIR_THROW` đang tự quay về; `STORMBREAKER` hiện là đòn beam. Không có vòng đời rìu ném ra, chờ rồi thu theo lần bấm thứ hai.
- `BatmanSkills.BATCLAW` hiện kéo entity hoặc dash đến điểm. Chưa có neo, dây, lực căng hay giữ quán tính để đu.
- Flash có trong `HeroRosterMigration`, catalog và handler. `ClassSelectionScreen` có phân trang; quyền chọn class hiện phụ thuộc permission. Chưa đủ bằng chứng để kết luận nguyên nhân trên pack của người dùng: QA phải kiểm tra roster từ server, phân trang/tìm kiếm, khóa và bản config được migrate.
- `ClassSwitchService` hiện thu tiền trước khi đổi class; chưa có quyền sở hữu class từ boss và nhánh chọn đầu tiên miễn phí ở luồng FantasyHub.
- `ClassSelectionRuntime` có trạng thái đã chọn class lưu theo UUID, nhưng không đủ để thay cho sổ quyền sở hữu và giao dịch nguyên liệu.
- `SVFrameMMOCobblemon` có adapter damage/status/battle protection, chưa có cầu nối riêng EXP Pokémon và AI hostile trong các phần đã khảo sát. Không coi việc có thể nhận damage là bằng chứng Pokémon chủ động tấn công.

## Cách tổ chức được đề xuất

**Đề xuất:** giữ combat hero trong FantasyHub; thêm module/JAR riêng `SVFrameWorldBoss`, tích hợp API native của SVFrameMMO/Lib và Cobblemon. Class ownership/starter entitlement có API tại MMO; world boss trao nguyên liệu, GUI đổi nguyên liệu qua API đó. EXP và hostility Pokémon nằm trong integration Cobblemon, không buộc người chơi cài boss mới nhận EXP hoặc gặp Pokémon hostile.

Hai cách khác đã cân nhắc: nhét toàn bộ boss vào FantasyHub khiến side mod không độc lập; phụ thuộc trực tiếp NovaRaids không đáp ứng sẵn boss mang class và sẽ đưa thêm runtime ngoài yêu cầu. Không chọn hai cách này.

NovaRaids được tham khảo tại commit `684f004b4e66859592c4b01ae53ab3cbdbfa3c72`: lịch có timezone, hàng đợi/địa điểm đang bận, setup/fight/win/loss, contribution và lịch sử raid. Triển khai độc lập trên native API; không chép source hay đóng gói NovaRaids. Nguồn: https://github.com/Unariginal/NovaRaids .

## 1. Stormbreaker ném và thu

- Thêm `THOR_STORMBREAKER_THROW`, tách khỏi Mjolnir và skill Stormbreaker hiện có. Là skill phải học, hiển thị trong danh sách để bind vào thanh kỹ năng.
- Cần cầm Stormbreaker thật để ném. Server giữ trạng thái `held → outbound → hovering → returning → held`; kích hoạt lần hai thu về ngay cả khi cooldown lượt ném chưa hết.
- Bay tối đa 20 block từ vị trí phóng, tốc độ mặc định 1.5 block/tick, hit radius mặc định 0.9 block; các thông số để trong config. Vật cản dừng lượt đi; lượt về đến tay hiện tại và xử lý collision rõ ràng, không đánh xuyên tường.
- Kiểm tra swept segment từng tick để rìu không bỏ qua mục tiêu giữa hai frame. Mỗi mục tiêu hợp lệ trúng tối đa một lần mỗi chiều; không dùng giới hạn 8 mục tiêu của AoE thông thường để bỏ sót mục tiêu trên đường rìu.
- “Toàn bộ entities” trong combat gồm mọi living entity hợp lệ và player, qua PvP/team/Cobblemon battle protections; không damage projectile, vật trang trí hay chính chủ.
- Không tạo thêm item rơi. Stack thật được giữ làm nguồn, hình cầm tay ẩn trong lúc rìu ở ngoài và đòn melee bằng rìu bị chặn. Đổi stack/slot, đổi class/world, chết hoặc disconnect kết thúc lease an toàn; không mất hoặc nhân đôi item.
- Quá 8 giây chưa thu thì tự thu. VFX model Stormbreaker thật xoay theo trục bay, điện bám đường, ánh chớp va chạm, âm phóng/thu và bắt rìu. Observer thấy cùng đường đi với server.

## 2. Batman: suit, grapple và VFX

- Chọn hướng **Batman Beyond đen/đỏ**, khác cả suit DCEU lẫn Comics xám/đen hiện tại: thân đen than, biểu tượng dơi đỏ lớn, mắt sáng, chi tiết đai/găng/ủng đọc rõ trên model Minecraft. Texture và icon mới đồng bộ; không dùng một lớp tint lên suit cũ.
- Cape Batman vẫn có scallop, luôn ghi đè cape skin; khi glide mở thành màng cánh có gân và chuyển động thấy rõ từ phía sau/cạnh.
- Thêm item thật `fantasyhub:batman_grappling_hook`, có model, icon, tooltip và định nghĩa RPG không mất độ bền. Dùng item yêu cầu class Batman và đã học Batclaw; skill Batclaw và item dùng chung cooldown/logic.
- Bấm dùng phóng móc tối đa 28 block. Trúng block tạo neo: dây có giới hạn chiều dài, trọng lực và lực căng để player đu với quán tính; giữ dùng để rút dây, Jump hoặc Sneak để thả. Không teleport/dash giả làm đu.
- Trúng entity/player thì kéo mục tiêu về khoảng cách gần an toàn, có resistance/control immunity và kiểm tra PvP/đội. Dây đứt khi vượt range, đổi dimension, mục tiêu mất hiệu lực hoặc bị chặn lâu. Không kéo xuyên block.
- Batarang có model chuyển động và vệt sáng; Smoke Takedown có khói thể tích nhiều lớp/đàn dơi; Detective Vision có xung scan và highlight; Disruptor có điện xung vào mục tiêu; Cape Combo có cung vung cape/impact; Batwing có bóng máy bay bay qua và chuỗi nổ theo thời gian.
- Tách telegraph trước đòn, action và impact; âm thanh đúng thời điểm, không chỉ tăng số particle cùng một kiểu vòng tròn.

## 3. Flash và khả năng tìm class

- Menu có tìm kiếm và tab hero, luôn liệt kê Flash và các class đã load, kể cả class còn khóa; hiện nguyên nhân khóa và cách mở.
- Flash: tia điện vàng/đỏ gắn chân tay, dải điện liên tục khi chạy, afterimage theo quãng đường thực, sonic boom và impact riêng cho từng đòn. Tốc độ thấp không tạo afterimage đứng chồng.
- QA account thường, không chỉ operator: roster đủ class, Flash tìm được/chọn được khi có quyền, không cần grant permission ẩn. Phân biệt “chưa mở khóa” với “không được load”.

## 4. EXP Pokémon và học skill

- EXP từ thắng trận Pokémon và hạ Pokémon hoang ở overworld đi vào class đang chọn qua hệ thống EXP/level native. Không lấy mọi lần party Pokémon tăng EXP làm một phần thưởng player độc lập vì Exp Share sẽ nhân EXP.
- Một sự kiện chiến thắng được ghi nhận một lần cho mỗi player; attribution của Pokémon do player sở hữu về trainer. Có công thức/giới hạn riêng cho battle và overworld trong config; không tính lại một mục tiêu qua cả event faint và death.
- Không thưởng EXP từ đánh Pokémon của mình, friendly duel hoặc boss summon thử nghiệm. Boss có chính sách EXP riêng khi encounter hoàn thành.
- Skill có hai điều kiện: đủ level class **và đã học**. Giao diện cho thấy level yêu cầu, giá điểm học, trạng thái khóa và nút học. Mặc định mỗi skill tốn 1 điểm, level class nhận điểm theo curve cấu hình.
- Bảy skill hiện tại theo mốc 1/5/10/15/20/25/30; Stormbreaker ném/thu mở ở level 12. Lúc chọn class cấp sẵn 1 điểm để người chơi tự học skill đầu, không mở tự động cả bộ.
- Thanh chiến đấu giữ tối đa 7 binding đang dùng; Thor có 8 skill để lựa chọn. Loại bỏ hard binding khiến skill thứ 8 không thể chọn. Slot trang bị/vũ khí native vẫn được bảo toàn.
- Cast từ phím, item, script/provider và recast đều qua điều kiện học/class/level server. Packet giả không bỏ qua khóa.
- Migration giữ các skill người chơi đã **chủ động học**. Quyền mở tự động do cấu hình cũ không chuyển thành toàn bộ skill đã học. Người chơi cũ nhận số điểm tương ứng level để phân bổ lại, không mất level class.

## 5. Pokémon hostile thật

- Mặc định Pokémon hoang đủ điều kiện chủ động tìm player survival/adventure, đuổi theo và dùng đòn gây damage thật. Có wind-up, tầm đánh, cooldown, leash và thời gian mất mục tiêu; không chỉ đổi nhãn hostile hoặc gây damage theo khoảng cách vô hình.
- Loại trừ Pokémon có chủ, đang bắt, busy, fainted, clone hoặc tham gia trận turn-based; creative/spectator không là mục tiêu. Khi chuyển sang trạng thái battle/capture phải dừng AI và các đòn đang chờ.
- Dùng native overworld move/damage adapter; chọn kỹ năng theo moveset với đòn melee/ranged fallback có telegraph. Type/resistance và RPG damage attribution vẫn hoạt động.
- Khi Fight or Flight hiện diện, chỉ một bên sở hữu AI chiến đấu trên từng Pokémon. Cần kiểm tra API/mixin thực tế và test tránh double attack; không chạy thêm vòng tick damage song song rồi coi là xong.
- Config điều chỉnh phạm vi phát hiện, loài/vùng ngoại lệ, cooldown và giới hạn số Pokémon cùng truy đuổi. Mặc định detect 16 block, leash 32 block, tối đa 4 attacker/player.

## 6. Side mod world boss

- JAR riêng `SVFrameWorldBoss`, config riêng: `bosses.json`, `locations.json`, `schedule.json`, `rewards.json`. Dùng server wall clock và timezone rõ ràng, không phụ thuộc ngày/đêm Minecraft.
- Mặc định đề xuất **Asia/Ho_Chi_Minh, 12:00 / 18:00 / 21:00 hàng ngày**, cảnh báo trước 5 phút và 1 phút; setup 60 giây, fight tối đa 10 phút. Boss luân phiên theo lịch, một encounter tại mỗi địa điểm; không đẻ chồng sau reload/restart.
- Lịch ghi occurrence ID đã xử lý bền vững. Server offline không sinh bù dồn hàng loạt khi mở lại. Địa điểm cấu hình phải có vị trí an toàn; bản phân phối cung cấp lệnh đặt arena tại chỗ, không tự phá địa hình để dựng arena.
- Hai adapter boss: Pokémon thật (species/moveset/scale/HP) và **NPC thân player mang class, suit, vũ khí, skill và AI cast thật**. Boss class không được thay bằng armor stand đứng yên hoặc zombie chỉ đổi tên.
- Class NPC dùng combat context dùng chung cho server actor và player để tái sử dụng phase damage/control/cue; không tạo tài khoản player giả trong database để ép chạy `HeroRuntime.Session`.
- Encounter `scheduled → setup → fight → won/lost → settlement → closed`; bossbar thể hiện HP/thời gian, thông báo, bảng đóng góp, đòn có telegraph né được. Mốc HP đổi phase/rotation; ra khỏi arena thì leash về, không truy đuổi toàn server.
- Boss không thể bắt, recall hay bỏ túi; summon/minion không tự thành nguồn nguyên liệu vô hạn. Dừng/admin abort/restart xử lý riêng, không tự coi là người chơi thua để phát quà.
- Damage thực đã áp dụng được cộng theo UUID player, kể cả projectile và Pokémon thuộc trainer; summon không thành player thứ hai. Người chỉ đứng AFK không có thưởng.
- Mặc định người có đóng góp hợp lệ nhận **12 Class Sigil khi thắng, 3 khi thua**, với ngưỡng tổng damage 0.5% HP boss hoặc mức đóng góp hỗ trợ cấu hình. Rời trận vẫn giữ lịch sử nhưng phải có tối thiểu 30 giây tham gia hợp lệ. Death không tự loại khỏi sổ đóng góp.
- Sổ settlement dùng encounter UUID + player UUID để không phát thưởng hai lần khi death/timeout đồng thời, reconnect hoặc restart. Offline/inventory đầy nhận qua hộp nhận thưởng, không vứt mất nguyên liệu.

## 7. Mở class và lựa chọn miễn phí

- Mỗi UUID mới có đúng **một lượt chọn miễn phí**, chọn trong roster được server cho phép (mặc định mọi class selectable, trừ DEFAULT). Không cần tiền hay nguyên liệu cho lượt này; lựa chọn mở quyền sở hữu class vĩnh viễn.
- Các class khác mặc định **60 Class Sigil/class**, có override từng class. Nguyên liệu là item RPG có định danh do server kiểm tra, không chấp nhận paper đổi tên/lore.
- GUI hiển thị class đang sở hữu, số nguyên liệu đang có/cần, lượt miễn phí còn lại và giao dịch xác nhận. Unlock và chọn class là hai trạng thái tách biệt; có thể mở class rồi chọn sau.
- Phí/cooldown đổi class đã được cấu hình trước đây giữ cho các lượt đổi sau; **không áp dụng cho lần chọn miễn phí**. Đặt chính sách này trong config để có thể tắt phí chuyển mà không thay giá mở khóa.
- Chi tiêu nguyên liệu, cấp ownership và dùng lượt miễn phí là giao dịch có transaction ID/rollback/recovery; không trừ vật phẩm hai lần khi spam click hoặc cấp quyền trước khi lưu thành công.
- Migration giữ class hiện tại/ownership hợp lệ trước đó. Player cũ đã chọn class không được tự nhận thêm lượt starter qua reconnect hoặc xóa client config; admin có lệnh grant/revoke/reset rõ ràng.

## 8. QA bắt buộc và bản giao

### Bằng chứng combat/visual

- Với **mỗi skill** Thor, Strange, Spider-Man, Batman: ghi clip trọn telegraph → cast → travel/channel → impact → recast/kết thúc; ảnh ở các mốc dễ xem, góc gần và góc observer, HUD hiện tên skill/mục tiêu. Thor bao gồm cả 8 skill mới.
- Đo health/damage thực đi cùng clip; không dùng chỉ số “cast accepted” thay cho chứng minh skill gây tác động. VFX và âm thanh kiểm tra trong native client; video tổng hợp có audio nếu môi trường capture được.
- Stormbreaker: >=10 mục tiêu trên đường đi; tất cả mục tiêu hợp lệ trúng một lần/chiều; mục tiêu ngoài đường không trúng; 20 block/wall/early recall; đổi slot/death/disconnect không mất hoặc dupe item.
- Grapple: neo địa hình, cung đu/quán tính, reel/release, kéo player có PvP và từ chối khi PvP tắt/kháng kéo. Batman nhảy và cape glide vẫn hoạt động.
- Flash: roster account thường, skill visuals từng đòn và afterimage khi chạy. Empty-handed Thor không render Stormbreaker; suit/icon/cape/độ bền/Detailed/BetterCombat không hồi quy.

### Bằng chứng progression/boss

- Player mới không có tiền chọn một class miễn phí, thử lượt thứ hai bị từ chối; restart vẫn giữ ownership. Tài khoản thường, không op.
- Skill chưa học: từ chối ở phím/item/provider/recast; học đủ điều kiện mới cast; skill thứ 8 bind được. Pokémon battle và overworld EXP thực sự tăng class EXP/level một lần.
- Pokémon hoang tự phát hiện/đuổi/đánh làm giảm HP player; dừng khi battle/capture; owned Pokémon không tự đánh chủ; test cùng Fight or Flight.
- Cả Pokémon boss và class NPC boss: spawn theo clock giả lập và lịch thật rút ngắn cho QA, tự cast, contribution nhiều player, win/loss, 12/3 sigil, thiếu/đủ 60 sigil mở class, offline/full inventory/reconnect/restart/settlement duplicate.
- Lưu report, clip, ảnh và thông tin môi trường. Bất kỳ mục chưa chạy trên pack 289 mod Windows phải được ghi là chưa xác minh, không đánh dấu pass suy diễn từ test nhỏ.

### Đóng gói

ZIP mới gồm FantasyHubClient, Bridge, framework/integration bị thay đổi, **SVFrameWorldBoss.jar**, config mẫu và migration, hướng dẫn đặt arena/lịch/giá mở khóa, checksum và bằng chứng QA. Dependencies bên thứ ba để riêng khi cần; không nhúng NovaRaids, Soulslike, Xaero hoặc runtime mod khác vào JAR của mình. Giữ nguyên bản giao 0.2.3 để rollback.

## Thứ tự thực hiện sau khi duyệt thiết kế

1. Ownership/starter/skill gate/EXP: chốt API tiến trình trước để item và boss không tạo đường bỏ qua.
2. Stormbreaker/grapple/suit/Flash + VFX bốn hero, đồng thời giữ startup geometry isolation.
3. Pokémon hostile, hai adapter boss, lịch/contribution/settlement và GUI nguyên liệu.
4. Kiểm thử native multiplayer, chụp/quay đủ từng skill và progression, rồi build/đóng gói đủ client + server + config.

Đây là thứ tự kiến trúc; kế hoạch triển khai theo file/test sẽ được lập sau khi thiết kế được duyệt.
