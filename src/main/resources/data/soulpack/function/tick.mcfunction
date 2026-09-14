function soulpack:watchdog

execute as @a[scores={pk_deaths=1..}] at @s run function soulpack:on_death

scoreboard players add @e[type=minecraft:marker,tag=soulpack_death_marker] pk_timer 1
execute as @e[type=minecraft:marker,tag=soulpack_death_marker,scores={pk_timer=2..200,pk_captured=0}] at @s run function soulpack:marker_scan

# Process each claimed backpack from the backpack's own context.
execute as @e[type=minecraft:item,tag=soulpack] at @s run function soulpack:process_claimed_item

kill @e[type=minecraft:marker,tag=soulpack_death_marker,scores={pk_captured=1}]
kill @e[type=minecraft:marker,tag=soulpack_death_marker,scores={pk_timer=201..}]
