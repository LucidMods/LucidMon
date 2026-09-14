# Read persistent config storage into scoreboard values.
execute store result score #area_x lucid_map run data get storage lucid_map:config area.x 1
execute store result score #area_z lucid_map run data get storage lucid_map:config area.z 1
execute store result score #interval_ms lucid_map run data get storage lucid_map:config interval_ms 1

# Sanity clamps.
execute if score #area_x lucid_map matches ..0 run scoreboard players set #area_x lucid_map 256
execute if score #area_z lucid_map matches ..0 run scoreboard players set #area_z lucid_map 256
execute if score #interval_ms lucid_map matches ..0 run scoreboard players set #interval_ms lucid_map 50

# Convert milliseconds to Minecraft ticks.
# One tick = 50 ms at 20 TPS. Adding 49 makes this a ceiling conversion,
# so the actual delay is never shorter than the requested millisecond value.
scoreboard players operation #interval_ticks lucid_map = #interval_ms lucid_map
scoreboard players operation #interval_ticks lucid_map += #fortynine lucid_map
scoreboard players operation #interval_ticks lucid_map /= #fifty lucid_map
execute if score #interval_ticks lucid_map matches ..0 run scoreboard players set #interval_ticks lucid_map 1

# Number of scan points = ceil(area / 256) + 1.
# This treats area.x and area.z as MINIMUM coverage and rounds outward to the
# 256-block scan grid.
scoreboard players operation #cols lucid_map = #area_x lucid_map
scoreboard players operation #cols lucid_map += #spacing_minus_one lucid_map
scoreboard players operation #cols lucid_map /= #spacing lucid_map
scoreboard players add #cols lucid_map 1

scoreboard players operation #rows lucid_map = #area_z lucid_map
scoreboard players operation #rows lucid_map += #spacing_minus_one lucid_map
scoreboard players operation #rows lucid_map /= #spacing lucid_map
scoreboard players add #rows lucid_map 1

scoreboard players operation #last_col lucid_map = #cols lucid_map
scoreboard players remove #last_col lucid_map 1
scoreboard players operation #last_row lucid_map = #rows lucid_map
scoreboard players remove #last_row lucid_map 1

scoreboard players operation #points lucid_map = #cols lucid_map
scoreboard players operation #points lucid_map *= #rows lucid_map
