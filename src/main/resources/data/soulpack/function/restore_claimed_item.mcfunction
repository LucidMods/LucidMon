tag @s add soulpack_active_source
execute as @a[tag=soulpack_restore_target,limit=1] run scoreboard players set @s pk_restored 0
execute as @a[tag=soulpack_restore_target,limit=1] run function soulpack:place_claimed_source_in_inventory

# Delete source only if player-side copy succeeded.
execute if entity @a[tag=soulpack_restore_target,scores={pk_restored=1},limit=1] run kill @s
execute as @a[tag=soulpack_restore_target,scores={pk_restored=1},limit=1] run scoreboard players set @s pk_pending 0
execute as @a[tag=soulpack_restore_target,scores={pk_restored=1},limit=1] run tellraw @s {"text":"Soulpack: exact backpack restored directly into your inventory.","color":"green"}

# If no empty slot, teleport the original to the owner's feet and unlock pickup.
execute if entity @s if entity @a[tag=soulpack_restore_target,scores={pk_restored=0},limit=1] run data modify entity @s Owner set from entity @a[tag=soulpack_restore_target,limit=1] UUID
execute if entity @s if entity @a[tag=soulpack_restore_target,scores={pk_restored=0},limit=1] run data merge entity @s {Age:-32768s,PickupDelay:0s,Invulnerable:1b}
execute if entity @s if entity @a[tag=soulpack_restore_target,scores={pk_restored=0},limit=1] run tp @s @a[tag=soulpack_restore_target,limit=1]
execute as @a[tag=soulpack_restore_target,scores={pk_restored=0},limit=1] run scoreboard players set @s pk_pending 0
execute as @a[tag=soulpack_restore_target,scores={pk_restored=0},limit=1] run tellraw @s {"text":"Soulpack: inventory full; protected backpack placed at your feet.","color":"yellow"}

tag @s remove soulpack_active_source
