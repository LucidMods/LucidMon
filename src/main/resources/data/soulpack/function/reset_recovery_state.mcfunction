scoreboard players set @s pk_pending 0
scoreboard players set @s pk_restored 0
scoreboard players reset @s pk_deaths
tellraw @s {"text":"Soulpack pending recovery state reset.","color":"gray"}
