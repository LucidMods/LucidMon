scoreboard players set #running lucid_map 0
scoreboard players set #timer lucid_map 0

execute as @a[tag=lucid_map_target,limit=1] at @e[type=minecraft:marker,tag=lucid_map_center,limit=1] run tp @s ~ ~ ~
tellraw @a[tag=lucid_map_target] {"text":"[LucidMap] Sweep stopped. Returned to center.","color":"yellow"}

execute at @e[type=minecraft:marker,tag=lucid_map_center,limit=1] run forceload remove ~ ~
tag @a remove lucid_map_target
kill @e[type=minecraft:marker,tag=lucid_map_cursor]
kill @e[type=minecraft:marker,tag=lucid_map_center]
