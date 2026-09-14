scoreboard players operation @s pk_owner = @e[type=minecraft:marker,tag=soulpack_marker_tmp,limit=1] pk_owner
tag @s add soulpack
data merge entity @s {Age:-32768s,PickupDelay:32767s,Invulnerable:1b}
scoreboard players set @e[type=minecraft:marker,tag=soulpack_marker_tmp,limit=1] pk_captured 1
