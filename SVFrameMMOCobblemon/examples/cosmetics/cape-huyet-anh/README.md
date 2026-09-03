# Huyết Ảnh Phi Phong

Particle cape dùng **hai backend trong cùng một cosmetic**, không có custom resource-pack asset:

- `MINECRAFT`: `minecraft:dust` màu đỏ huyết dựng thân áo.
- `COBBLEMON`: `cobblemon:shadowclaw_target` làm điểm nhấn ở hai vai.
- `BACK` local offsets được xoay theo hướng nhìn của player, nên toàn bộ silhouette luôn nằm sau lưng.
- Không có Snowstorm JSON/PNG riêng, không cần merge/generate resource pack.

## Cài đặt

Copy:

`config/SVFrameMMOCobblemon/cosmetics/back_huyet_anh_phi_phong.yml`

vào cùng đường dẫn trên server rồi chạy:

`/cosmetics reload`

Sau đó:

`/cosmetics grant <player> back_huyet_anh_phi_phong`
`/cosmetics equip back_huyet_anh_phi_phong`

## Backend theo layer

Root của YAML là default. Mỗi layer có thể override riêng:

```yaml
backend: MINECRAFT
particle: minecraft:dust
color: "#7A0019"
scale: 0.90

phases:
  WHILE_EQUIPPED:
    - anchor: BACK
      offset-x: 0.0
      offset-y: 0.2
      offset-z: -0.3
    - backend: COBBLEMON
      particle: cobblemon:shadowclaw_target
      anchor: BACK
      offset-x: 0.4
      offset-y: 0.3
      offset-z: -0.3
```

`AUTO` vẫn là mặc định để YAML cũ chạy: `minecraft:*` đi native Minecraft; namespace khác đi Snowstorm. Legacy `svframe_dust:RRGGBB/scale` vẫn được đọc để không làm hỏng config 0.1.14, nhưng config mới nên dùng `minecraft:dust` + `color` + `scale`.
