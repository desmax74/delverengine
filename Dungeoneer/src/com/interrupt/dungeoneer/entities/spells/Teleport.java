package com.interrupt.dungeoneer.entities.spells;

import java.util.Random;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector3;
import com.interrupt.dungeoneer.Audio;
import com.interrupt.dungeoneer.entities.Actor;
import com.interrupt.dungeoneer.entities.Door;
import com.interrupt.dungeoneer.entities.DynamicLight;
import com.interrupt.dungeoneer.entities.Entity;
import com.interrupt.dungeoneer.entities.Monster;
import com.interrupt.dungeoneer.entities.Mover;
import com.interrupt.dungeoneer.entities.Particle;
import com.interrupt.dungeoneer.entities.Player;
import com.interrupt.dungeoneer.entities.PositionedSound;
import com.interrupt.dungeoneer.entities.items.Weapon.DamageType;
import com.interrupt.dungeoneer.entities.triggers.Trigger;
import com.interrupt.dungeoneer.game.Game;
import com.interrupt.dungeoneer.game.Level;
import com.interrupt.dungeoneer.game.Options;
import com.interrupt.dungeoneer.serializers.KryoSerializer;
import com.interrupt.dungeoneer.tiles.Tile;

public class Teleport extends Spell {
	
	public Teleport() { }
	
	public Teleport(DamageType damageType) {
		this.damageType = damageType;
	}
	
	@Override
	public void doCast(Entity owner, Vector3 direction, Vector3 position) {
		Vector3 pos = new Vector3(owner.x, owner.y, owner.z);
		teleport(owner, Game.rand.nextInt());
	}

	public void doCast(Vector3 pos, Vector3 direction) {
		Level level = Game.GetLevel();
		
		int seed = (int)pos.x * level.width + (int)pos.y;
		
		for(Entity e : level.entities) {
			if(e.isActive && e.isDynamic && !(e instanceof Door) && !(e instanceof Mover) && !(e instanceof Trigger) && Math.abs(e.x - pos.x) < 0.5f + e.collision.x && Math.abs(e.y - pos.y) < 0.5f + e.collision.y) {
				teleport(e, seed);
			}
		}
		
		Player p = Game.instance.player;
		if(Math.abs(p.x + 0.5f - pos.x) < 0.5f + p.collision.x && Math.abs(p.y + 0.5f - pos.y) < 0.5f + p.collision.y) {
			teleport(p, seed);
			p.history.teleported();
		}
	}
	
	public void teleport(Entity e, int seed) {
		Level level = Game.GetLevel();
		if(level.dungeonLevel == 0) return;
		
		Random r = new Random(seed);
		int tries = 0;
		while(tries++ < 1000) {
			int checkX = r.nextInt(level.width);
			int checkY = r.nextInt(level.height);
			Tile checkTile = level.getTile(checkX, checkY);
			if(checkTile.CanSpawnHere()) {
				e.x = checkX + 0.5f;
				e.y = checkY + 0.5f;
				
				float height1 = checkTile.getFloorHeight(e.x - e.collision.x, e.y - e.collision.y);
				float height2 = checkTile.getFloorHeight(e.x - e.collision.x, e.y + e.collision.y);
				float height3 = checkTile.getFloorHeight(e.x + e.collision.x, e.y - e.collision.y);
				float height4 = checkTile.getFloorHeight(e.x + e.collision.x, e.y + e.collision.y);
				float max = Math.max(height4, Math.max(height3, Math.max(height1, height2)));
				
				e.z = max + 0.5f;
				
				if(e instanceof Player) {
					e.x -= 0.5;
					e.y -= 0.5;
				}
				
				Entity toFrag = level.checkEntityCollision(e.x, e.y, e.z, e.collision, e);
				if(toFrag != null && toFrag instanceof Actor) {
					((Actor)toFrag).hp = 0;
				}
				else if(toFrag != null) {
					// this didn't work out, try again
					continue;
				}

				doCastEffect(new Vector3((float)checkX + 0.5f, (float)checkY + 0.5f, checkTile.floorHeight + 0.5f), level, e);
				
				return;
			}
		}
	}

	@Override
	protected void doCastEffect(Vector3 pos, Level level, Entity owner) {

		// might have a custom vfx
		if(castVfx != null) {
			Entity vfx = (Entity) KryoSerializer.copyObject(castVfx);

			vfx.x += pos.x;
			vfx.y += pos.y;
			vfx.z += pos.z;

			Game.GetLevel().SpawnEntity(vfx);
			return;
		}

		Random r = Game.rand;
		int particleCount = 20;
		particleCount *= Options.instance.gfxQuality;
		if(particleCount <= 0) particleCount = 1;
		
		for(int i = 0; i < particleCount; i++)
		{
			int speed = r.nextInt(45) + 10;
			Particle part = new Particle(pos.x + r.nextFloat() - 0.5f, pos.y + r.nextFloat() - 0.5f, pos.z + r.nextFloat() * 0.9f - 0.45f, 0f, 0f, 0f, 0, Color.ORANGE, true);
			part.floating = true;
			part.playAnimation(8, 12, speed);
			part.checkCollision = false;

			if(owner != null && owner instanceof Player) {
				// Push the particles in the camera direction to be more visible to the player
				part.x += Game.camera.direction.x * 0.5f;
				part.y += Game.camera.direction.z * 0.5f;
				part.z += Game.camera.direction.y * 0.5f - 0.25f;
			}

			level.SpawnNonCollidingEntity(part);
		}
		
		level.SpawnNonCollidingEntity( new DynamicLight(pos.x,pos.y,pos.z, new Vector3(Color.ORANGE.r * 2f, Color.ORANGE.g * 2f, Color.ORANGE.b * 2f)).startLerp(new Vector3(0,0,0), 40, true).setHaloMode(Entity.HaloMode.BOTH) );
		
		Audio.playPositionedSound("trap_tele.mp3", new Vector3(pos.x, pos.y, pos.z), 0.6f, 12f);
	}
}
