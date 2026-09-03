# Huyết Ảnh Phi Phong

Particle cape thuần server-side, không cần resource pack.

- Slot: `BACK`
- Particle: `svframe_dust:7a0019/0.9`
- Renderer xoay toàn bộ local offsets theo hướng nhìn của player, nên áo choàng luôn nằm sau lưng.
- Không có Snowstorm JSON/PNG, không Polymer asset, không merge/generate resource pack.

## Cài đặt

Copy:

`config/SVFrameMMOCobblemon/cosmetics/back_huyet_anh_phi_phong.yml`

vào cùng đường dẫn trên server rồi chạy:

`/cosmetics reload`

Sau đó:

`/cosmetics grant <player> back_huyet_anh_phi_phong`
`/cosmetics equip back_huyet_anh_phi_phong`

## Vanilla dust pseudo-id

Format:

`svframe_dust:RRGGBB/scale`

Ví dụ `svframe_dust:7a0019/0.9` là đỏ huyết, scale 0.9. Renderer chuyển trực tiếp thành `DustParticleEffect`; không có asset client-side nào cần thêm.
