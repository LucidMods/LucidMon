# @s = claimed dropped backpack item
scoreboard players operation #restore pk_global = @s pk_owner

# Mark the matching online player globally.
tag @a remove soulpack_restore_target
execute as @a if score @s pk_owner = #restore pk_global run tag @s add soulpack_restore_target

# Only continue once the owner is online and alive.
execute if entity @a[tag=soulpack_restore_target,limit=1] unless entity @a[tag=soulpack_restore_target,limit=1,nbt={Health:0.0f}] run function soulpack:restore_claimed_item

tag @a remove soulpack_restore_target
