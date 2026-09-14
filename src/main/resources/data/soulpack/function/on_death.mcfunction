scoreboard players add #next pk_global 1
scoreboard players operation @s pk_owner = #next pk_global
scoreboard players set @s pk_pending 1
scoreboard players set @s pk_restored 0

tag @s add soulpack_owner_tmp
summon minecraft:marker ~ ~ ~ {Tags:["soulpack_death_marker","soulpack_marker_new"]}
execute as @e[type=minecraft:marker,tag=soulpack_marker_new,distance=..2,sort=nearest,limit=1] run scoreboard players operation @s pk_owner = @a[tag=soulpack_owner_tmp,limit=1] pk_owner
scoreboard players set @e[type=minecraft:marker,tag=soulpack_marker_new,distance=..2,sort=nearest,limit=1] pk_timer 0
scoreboard players set @e[type=minecraft:marker,tag=soulpack_marker_new,distance=..2,sort=nearest,limit=1] pk_captured 0
tag @e[type=minecraft:marker,tag=soulpack_marker_new,distance=..2,sort=nearest,limit=1] remove soulpack_marker_new
tag @s remove soulpack_owner_tmp

scoreboard players reset @s pk_deaths
