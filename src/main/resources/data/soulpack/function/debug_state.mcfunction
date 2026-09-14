tellraw @s {"text":"--- Soulpack debug state ---","color":"aqua"}
scoreboard players get @s pk_deaths
scoreboard players get @s pk_pending
scoreboard players get @s pk_owner
scoreboard players get @s pk_restored
execute at @s run tellraw @s [{"text":"Nearby death markers: ","color":"gray"},{"selector":"@e[type=minecraft:marker,tag=soulpack_death_marker,distance=..32]"}]
execute at @s run tellraw @s [{"text":"Nearby claimed bags: ","color":"gray"},{"selector":"@e[type=minecraft:item,tag=soulpack,distance=..32]"}]
