// ── Constants ──────────────────────────────────────────────────────────────
let W = window.innerWidth;
let H = window.innerHeight;
const STATES = { MENU: 'MENU', PLAYING: 'PLAYING', GAME_OVER: 'GAME_OVER', LEVEL_UP: 'LEVEL_UP' };

const PLAYER_SPEED = 3;
const PLAYER_MAX_HP = 100;
const BULLET_SPEED = 7;
const BULLET_LIFETIME = 120; // frames
const FIRE_COOLDOWN = 18;    // frames between shots (base)
const XP_PICKUP_RADIUS = 35; // distance at which player auto-vacuums an orb
const ENEMY_DAMAGE = 0.15;   // HP drained per frame while touching player

// Enemy visual variants
const ENEMY_COLORS = {
  slow:   { fill: '#cc3333', outline: '#ff6666' },
  medium: '#e67e22',
  fast:   { fill: '#9b59b6', outline: '#c39bd3' },
  heavy:  { fill: '#1a5276', outline: '#2e86c1' },
};

// ── Leaderboard ────────────────────────────────────────────────────────────
const Leaderboard = {
  KEY: 'sth_scores',
  MAX: 10,
  load() {
    try { return JSON.parse(localStorage.getItem(this.KEY)) || []; }
    catch { return []; }
  },
  save(score, wave, level) {
    const entries = this.load();
    const date = new Date().toLocaleDateString('en-GB', { day: '2-digit', month: '2-digit', year: '2-digit' });
    entries.push({ score, wave, level, date });
    entries.sort((a, b) => b.score - a.score);
    entries.splice(this.MAX);
    localStorage.setItem(this.KEY, JSON.stringify(entries));
    return entries;
  },
};

// ── Canvas setup ───────────────────────────────────────────────────────────
const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');

function resizeCanvas() {
  W = window.innerWidth;
  H = window.innerHeight;
  canvas.width  = W;
  canvas.height = H;
}
resizeCanvas();
window.addEventListener('resize', resizeCanvas);

// ── Input ──────────────────────────────────────────────────────────────────
const keys = {};
window.addEventListener('keydown', e => { keys[e.key] = true; });
window.addEventListener('keyup',   e => { keys[e.key] = false; });

// ── Utility ────────────────────────────────────────────────────────────────
function dist(a, b) {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function randEdgePos(cx = W / 2, cy = H / 2) {
  const margin = 180; // far enough off-screen that enemies aren't visible on spawn
  const side = Math.floor(Math.random() * 4);
  switch (side) {
    case 0: return { x: cx - W / 2 + Math.random() * W, y: cy - H / 2 - margin };
    case 1: return { x: cx + W / 2 + margin,            y: cy - H / 2 + Math.random() * H };
    case 2: return { x: cx - W / 2 + Math.random() * W, y: cy + H / 2 + margin };
    default: return { x: cx - W / 2 - margin,           y: cy - H / 2 + Math.random() * H };
  }
}

// ── Player ─────────────────────────────────────────────────────────────────
class Player {
  constructor(weapon = WEAPONS[0]) {
    this.x  = W / 2;
    this.y  = H / 2;
    this.r  = 16;
    this.hp = PLAYER_MAX_HP;
    this.maxHp = PLAYER_MAX_HP;
    this.aimAngle = 0;
    this.fireCooldown = 0;
    this.level = 1;
    this.xp = 0;
    this.xpToNext = 10;
    this.levelUpFlash = 0;
    this.pendingLevelUp = false;
    // Weapon base stats (upgrades add on top)
    this.weapon            = weapon;
    this.baseFireCD        = weapon.fireCD;
    this.bulletDamage      = weapon.damage;
    this.bulletSize        = weapon.bulletSize;
    this.pierce            = weapon.pierce;
    this.burstCount        = weapon.burstCount;
    this.spreadAngle       = weapon.spreadAngle;
    this.bulletSpeed       = weapon.bulletSpeed;
    this.bulletColor       = weapon.color;
    this.bulletLifetime    = weapon.bulletLifetime;
    // Upgrade-only stats
    this.moveSpeed         = PLAYER_SPEED;
    this.regenRate         = 0;
    this.fireCooldownBonus = 0;
    this.luck              = 0;
    this.evasion           = 0;   // 0–0.40 chance to dodge a hit entirely
    this.lifesteal         = 0;   // 0–0.30 fraction of bullet damage healed
    this.armor             = 0;   // flat damage subtracted from each hit
    this.critChance        = 0;   // 0–0.40 chance for bullets to deal ×2 damage
    this.thorns            = 0;   // damage dealt per frame to touching enemies
    this.dodgeFlash        = 0;   // frames to show DODGE! text
    this.activePowerups    = {}; // { type: framesRemaining }
    this.ownedItems        = new Set(); // item ids equipped
  }

  hasPowerup(type) { return (this.activePowerups[type] || 0) > 0; }

  takeDamage(amount) {
    if (this.hasPowerup('shield')) return;
    if (this.evasion > 0 && Math.random() < this.evasion) {
      this.dodgeFlash = 40;
      return;
    }
    this.hp -= Math.max(0.01, amount - this.armor);
  }

  activatePowerup(type) {
    const cfg = POWERUP_CONFIG[type];
    if (cfg.duration > 0) {
      this.activePowerups[type] = cfg.duration;
    } else if (type === 'heal') {
      this.hp = Math.min(this.maxHp, this.hp + 40);
    }
  }

  get fireCooldownMax() {
    const maxBonus = Math.floor(this.baseFireCD * 0.4);
    let cd = Math.max(4, this.baseFireCD - Math.min(this.fireCooldownBonus, maxBonus));
    if (this.hasPowerup('enrage')) cd = Math.max(4, Math.floor(cd * 0.6));
    return cd;
  }

  addXP(amount) {
    this.xp += amount;
    while (this.xp >= this.xpToNext) {
      this.xp -= this.xpToNext;
      this.level++;
      this.xpToNext = Math.floor(this.xpToNext * 1.6);
      this.pendingLevelUp = true;
    }
  }

  update(enemies) {
    // Movement
    let dx = 0, dy = 0;
    if (keys['w'] || keys['W'] || keys['ArrowUp'])    dy -= 1;
    if (keys['s'] || keys['S'] || keys['ArrowDown'])  dy += 1;
    if (keys['a'] || keys['A'] || keys['ArrowLeft'])  dx -= 1;
    if (keys['d'] || keys['D'] || keys['ArrowRight']) dx += 1;
    if (dx !== 0 && dy !== 0) { dx *= 0.707; dy *= 0.707; }
    const spd = this.moveSpeed * (this.hasPowerup('whirlwind') ? 1.8 : 1);
    this.x += dx * spd;
    this.y += dy * spd;

    // Regeneration
    if (this.regenRate > 0) this.hp = Math.min(this.maxHp, this.hp + this.regenRate);

    // Aim toward closest enemy
    if (enemies.length > 0) {
      let closest = enemies.reduce((best, e) =>
        dist(this, e) < dist(this, best) ? e : best, enemies[0]);
      this.aimAngle = Math.atan2(closest.y - this.y, closest.x - this.x);
      this.closestEnemy = closest;
    } else {
      this.closestEnemy = null;
    }

    if (this.fireCooldown > 0) this.fireCooldown--;
    if (this.levelUpFlash > 0) this.levelUpFlash--;
    if (this.dodgeFlash   > 0) this.dodgeFlash--;

    // Tick active powerups
    for (const type of Object.keys(this.activePowerups)) {
      this.activePowerups[type]--;
      if (this.activePowerups[type] <= 0) delete this.activePowerups[type];
    }
  }

  tryFire(bullets) {
    if (this.fireCooldown === 0 && this.closestEnemy) {
      for (let i = 0; i < this.burstCount; i++) {
        const spread = (i - (this.burstCount - 1) / 2) * this.spreadAngle;
        const crit = this.critChance > 0 && Math.random() < this.critChance;
        const dmg  = this.bulletDamage * (this.hasPowerup('enrage') ? 1.5 : 1) * (crit ? 2 : 1);
        const size = crit ? this.bulletSize * 1.4 : this.bulletSize;
        const col  = crit ? '#fff' : this.bulletColor;
        bullets.push(new Bullet(
          this.x, this.y,
          this.aimAngle + spread,
          dmg, size,
          this.pierce, this.bulletSpeed, col, this.bulletLifetime
        ));
      }
      this.fireCooldown = this.fireCooldownMax;
    }
  }

  draw() {
    // Shield aura
    if (this.hasPowerup('shield')) {
      ctx.save();
      ctx.globalAlpha = 0.5 + Math.sin(Date.now() * 0.01) * 0.3;
      ctx.shadowColor = '#3498db';
      ctx.shadowBlur = 20;
      ctx.strokeStyle = '#85c1e9';
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.arc(this.x, this.y, this.r + 10, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();
    }

    // Dodge text
    if (this.dodgeFlash > 0) {
      const alpha = this.dodgeFlash / 40;
      const rise  = (40 - this.dodgeFlash) * 0.8;
      ctx.save();
      ctx.globalAlpha = alpha;
      ctx.fillStyle = '#f1c40f';
      ctx.font = 'bold 14px Courier New';
      ctx.textAlign = 'center';
      ctx.fillText('DODGE!', this.x, this.y - this.r - 10 - rise);
      ctx.restore();
    }

    // Level-up aura
    if (this.levelUpFlash > 0) {
      ctx.save();
      ctx.globalAlpha = this.levelUpFlash / 60;
      ctx.shadowColor = '#f1c40f';
      ctx.shadowBlur = 20;
      ctx.strokeStyle = '#f1c40f';
      ctx.lineWidth = 3;
      ctx.beginPath();
      ctx.arc(this.x, this.y, this.r + 8, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();
    }

    // Aim line
    if (this.closestEnemy) {
      ctx.save();
      ctx.strokeStyle = 'rgba(255, 80, 80, 0.25)';
      ctx.lineWidth = 1;
      ctx.setLineDash([6, 6]);
      ctx.beginPath();
      ctx.moveTo(this.x, this.y);
      ctx.lineTo(this.closestEnemy.x, this.closestEnemy.y);
      ctx.stroke();
      ctx.restore();
    }

    // Triangle body pointing in aim direction
    ctx.save();
    ctx.translate(this.x, this.y);
    ctx.rotate(this.aimAngle);
    ctx.beginPath();
    ctx.moveTo(this.r, 0);
    ctx.lineTo(-this.r * 0.7, -this.r * 0.6);
    ctx.lineTo(-this.r * 0.7,  this.r * 0.6);
    ctx.closePath();
    ctx.fillStyle = '#4fc3f7';
    ctx.fill();
    ctx.strokeStyle = '#81d4fa';
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.restore();

    // HP bar below player
    const bw = 40, bh = 5;
    const bx = this.x - bw / 2;
    const by = this.y + this.r + 6;
    ctx.fillStyle = '#333';
    ctx.fillRect(bx, by, bw, bh);
    ctx.fillStyle = this.hp > 40 ? '#2ecc71' : this.hp > 20 ? '#f39c12' : '#e74c3c';
    ctx.fillRect(bx, by, bw * (this.hp / this.maxHp), bh);
  }
}

// ── Enemy ──────────────────────────────────────────────────────────────────
class Enemy {
  constructor(x, y, type, wave = 1) {
    this.x = x;
    this.y = y;
    this.type = type;
    this.r = type === 'elite' ? 26 : type === 'heavy' ? 18 : type === 'slow' || type === 'charger' ? 14 : 12;
    const stats = {
      slow:    { hp: 3,  speed: 1.0 },
      medium:  { hp: 5,  speed: 1.5 },
      fast:    { hp: 3,  speed: 2.5 },
      heavy:   { hp: 8,  speed: 0.8 },
      charger: { hp: 6,  speed: 0.6 },
      shooter: { hp: 4,  speed: 1.1 },
      elite:   { hp: 60, speed: 1.0 },
    };
    // HP grows 12% per wave; speed grows 3.5% per wave (elites scale harder)
    const hpScale    = type === 'elite' ? 1 + (wave - 1) * 0.25 : 1 + (wave - 1) * 0.12;
    const speedScale = 1 + (wave - 1) * 0.035;
    this.speed  = stats[type].speed * speedScale;
    this.maxHp  = Math.ceil(stats[type].hp * hpScale);
    this.hp     = this.maxHp;

    // Charger state machine
    if (type === 'charger') {
      this.chargeState  = 'idle';   // idle → windup → dash → cooldown
      this.chargeTimer  = 80 + Math.floor(Math.random() * 40); // frames until next windup
      this.dashAngle    = 0;
      this.dashSpeed    = 0;
    }

    // Shooter state
    if (type === 'shooter') {
      this.shootTimer = 90 + Math.floor(Math.random() * 60); // frames until first shot
      this.preferredDist = 200; // tries to stay this far from player
    }
  }

  update(player, enemyBullets) {
    if (this.type === 'charger') {
      this._updateCharger(player);
      return;
    }
    if (this.type === 'shooter') {
      this._updateShooter(player, enemyBullets);
      return;
    }
    const angle = Math.atan2(player.y - this.y, player.x - this.x);
    const spd = Math.min(this.speed, player.moveSpeed * 0.95);
    this.x += Math.cos(angle) * spd;
    this.y += Math.sin(angle) * spd;
  }

  _updateShooter(player, enemyBullets) {
    const angle = Math.atan2(player.y - this.y, player.x - this.x);
    const d     = dist(this, player);
    const spd   = Math.min(this.speed, player.moveSpeed * 0.95);

    // Strafe: keep preferred distance — back away if too close, close in if too far
    if (d < this.preferredDist - 30) {
      // Too close — retreat
      this.x -= Math.cos(angle) * spd;
      this.y -= Math.sin(angle) * spd;
    } else if (d > this.preferredDist + 60) {
      // Too far — move in
      this.x += Math.cos(angle) * spd;
      this.y += Math.sin(angle) * spd;
    } else {
      // In range — strafe sideways
      this.x += Math.cos(angle + Math.PI / 2) * spd * 0.6;
      this.y += Math.sin(angle + Math.PI / 2) * spd * 0.6;
    }

    // Shoot
    this.shootTimer--;
    if (this.shootTimer <= 0) {
      const shootAngle = Math.atan2(player.y - this.y, player.x - this.x);
      enemyBullets.push(new EnemyBullet(this.x, this.y, shootAngle));
      this.shootTimer = 80 + Math.floor(Math.random() * 40);
    }
  }

  _updateCharger(player) {
    this.chargeTimer--;
    if (this.chargeState === 'idle') {
      // Slowly drift toward player while waiting
      const angle = Math.atan2(player.y - this.y, player.x - this.x);
      this.x += Math.cos(angle) * this.speed;
      this.y += Math.sin(angle) * this.speed;
      if (this.chargeTimer <= 0) {
        this.chargeState = 'windup';
        this.chargeTimer = 70; // windup duration
        this.dashAngle   = Math.atan2(player.y - this.y, player.x - this.x);
      }
    } else if (this.chargeState === 'windup') {
      // Stand still — update target angle during first 80% of windup, then lock it
      if (this.chargeTimer > 14) {
        this.dashAngle = Math.atan2(player.y - this.y, player.x - this.x);
      }
      if (this.chargeTimer <= 0) {
        this.chargeState = 'dash';
        this.chargeTimer = 35; // dash duration
        this.dashSpeed   = player.moveSpeed * 3.5;
      }
    } else if (this.chargeState === 'dash') {
      this.x += Math.cos(this.dashAngle) * this.dashSpeed;
      this.y += Math.sin(this.dashAngle) * this.dashSpeed;
      // Clamp to arena
      if (this.chargeTimer <= 0) {
        this.chargeState = 'cooldown';
        this.chargeTimer = 50;
      }
    } else if (this.chargeState === 'cooldown') {
      if (this.chargeTimer <= 0) {
        this.chargeState = 'idle';
        this.chargeTimer = 80 + Math.floor(Math.random() * 40);
      }
    }
  }

  draw() {
    if (this.type === 'elite') {
      // Glowing gold body
      ctx.save();
      ctx.shadowColor = '#f1c40f';
      ctx.shadowBlur = 18;
      ctx.beginPath();
      ctx.arc(this.x, this.y, this.r, 0, Math.PI * 2);
      ctx.fillStyle = '#7d6608';
      ctx.fill();
      ctx.strokeStyle = '#f1c40f';
      ctx.lineWidth = 3;
      ctx.stroke();
      ctx.restore();

      // Always-visible HP bar
      const bw = this.r * 2 + 20;
      const bx = this.x - bw / 2;
      const by = this.y - this.r - 20;
      const ratio = this.hp / this.maxHp;
      ctx.fillStyle = '#111';
      ctx.fillRect(bx, by, bw, 7);
      ctx.fillStyle = ratio > 0.5 ? '#f1c40f' : ratio > 0.25 ? '#e67e22' : '#e74c3c';
      ctx.fillRect(bx, by, bw * ratio, 7);
      ctx.strokeStyle = '#888';
      ctx.lineWidth = 1;
      ctx.strokeRect(bx, by, bw, 7);

      // ELITE label
      ctx.fillStyle = '#f1c40f';
      ctx.font = 'bold 9px Courier New';
      ctx.textAlign = 'center';
      ctx.fillText('ELITE', this.x, by - 3);
      return;
    }

    if (this.type === 'charger') { this._drawCharger(); return; }
    if (this.type === 'shooter') { this._drawShooter(); return; }

    const colors = {
      slow:   { fill: '#cc3333', outline: '#ff8080' },
      medium: { fill: '#e67e22', outline: '#f0a050' },
      fast:   { fill: '#9b59b6', outline: '#c39bd3' },
      heavy:  { fill: '#1a5276', outline: '#2e86c1' },
    };
    const c = colors[this.type];

    ctx.beginPath();
    ctx.arc(this.x, this.y, this.r, 0, Math.PI * 2);
    ctx.fillStyle = c.fill;
    ctx.fill();
    ctx.strokeStyle = c.outline;
    ctx.lineWidth = 2;
    ctx.stroke();

    // HP bar above enemy (only when damaged)
    if (this.hp < this.maxHp) {
      const bw = this.r * 2 + 4;
      const bx = this.x - bw / 2;
      const by = this.y - this.r - 8;
      ctx.fillStyle = '#1a1a1a';
      ctx.fillRect(bx, by, bw, 4);
      ctx.fillStyle = '#e74c3c';
      ctx.fillRect(bx, by, bw * (this.hp / this.maxHp), 4);
    }
  }

  _drawShooter() {
    // Teal diamond body
    ctx.save();
    ctx.translate(this.x, this.y);
    ctx.rotate(Math.PI / 4);
    const s = this.r * 1.1;
    ctx.beginPath();
    ctx.rect(-s / 2, -s / 2, s, s);
    ctx.fillStyle = '#117a65';
    ctx.fill();
    ctx.strokeStyle = '#1abc9c';
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.restore();

    // Muzzle flash indicator — small pulsing dot showing it can shoot
    if (this.shootTimer < 25) {
      const pulse = 1 - this.shootTimer / 25;
      ctx.save();
      ctx.globalAlpha = pulse;
      ctx.beginPath();
      ctx.arc(this.x, this.y, this.r + 5 + pulse * 6, 0, Math.PI * 2);
      ctx.strokeStyle = '#e74c3c';
      ctx.lineWidth = 2;
      ctx.stroke();
      ctx.restore();
    }

    // HP bar (when damaged)
    if (this.hp < this.maxHp) {
      const bw = this.r * 2 + 4;
      const bx = this.x - bw / 2;
      const by = this.y - this.r - 10;
      ctx.fillStyle = '#1a1a1a';
      ctx.fillRect(bx, by, bw, 4);
      ctx.fillStyle = '#e74c3c';
      ctx.fillRect(bx, by, bw * (this.hp / this.maxHp), 4);
    }
  }

  _drawCharger() {
    ctx.save();
    const isDash   = this.chargeState === 'dash';
    const isWindup = this.chargeState === 'windup';

    // Body — bright red-orange, glows during dash
    if (isDash) {
      ctx.shadowColor = '#ff4500';
      ctx.shadowBlur  = 20;
    }
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.r, 0, Math.PI * 2);
    ctx.fillStyle   = isDash ? '#ff4500' : '#c0392b';
    ctx.fill();
    ctx.strokeStyle = '#ff6b35';
    ctx.lineWidth   = 2;
    ctx.stroke();
    ctx.restore();

    // Windup indicator: pulsing ring + arrow pointing at locked target
    if (isWindup) {
      const progress  = 1 - this.chargeTimer / 70; // 0→1 as windup completes
      const ringR     = this.r + 6 + progress * 10;
      const alpha     = 0.4 + progress * 0.6;

      ctx.save();
      ctx.globalAlpha = alpha;
      // Expanding ring
      ctx.beginPath();
      ctx.arc(this.x, this.y, ringR, 0, Math.PI * 2);
      ctx.strokeStyle = '#ff4500';
      ctx.lineWidth   = 2 + progress * 3;
      ctx.stroke();

      // Arrow showing dash direction
      const ax   = this.x + Math.cos(this.dashAngle) * (this.r + 18 + progress * 10);
      const ay   = this.y + Math.sin(this.dashAngle) * (this.r + 18 + progress * 10);
      const aLen = 10 + progress * 6;
      ctx.strokeStyle = '#ffcc00';
      ctx.lineWidth   = 3;
      ctx.beginPath();
      ctx.moveTo(this.x + Math.cos(this.dashAngle) * (this.r + 4), this.y + Math.sin(this.dashAngle) * (this.r + 4));
      ctx.lineTo(ax, ay);
      ctx.stroke();
      // Arrowhead
      ctx.beginPath();
      ctx.moveTo(ax, ay);
      ctx.lineTo(ax - Math.cos(this.dashAngle - 0.5) * aLen, ay - Math.sin(this.dashAngle - 0.5) * aLen);
      ctx.moveTo(ax, ay);
      ctx.lineTo(ax - Math.cos(this.dashAngle + 0.5) * aLen, ay - Math.sin(this.dashAngle + 0.5) * aLen);
      ctx.stroke();
      ctx.restore();
    }

    // HP bar (when damaged)
    if (this.hp < this.maxHp) {
      const bw = this.r * 2 + 4;
      const bx = this.x - bw / 2;
      const by = this.y - this.r - 8;
      ctx.fillStyle = '#1a1a1a';
      ctx.fillRect(bx, by, bw, 4);
      ctx.fillStyle = '#e74c3c';
      ctx.fillRect(bx, by, bw * (this.hp / this.maxHp), 4);
    }
  }
}

// ── Bullet ─────────────────────────────────────────────────────────────────
class Bullet {
  constructor(x, y, angle, damage = 1, size = 4, pierce = false, speed = BULLET_SPEED, color = '#ffe082', lifetime = BULLET_LIFETIME) {
    this.x    = x;
    this.y    = y;
    this.vx   = Math.cos(angle) * speed;
    this.vy   = Math.sin(angle) * speed;
    this.life = lifetime;
    this.damage = damage;
    this.size   = size;
    this.pierce = pierce;
    this.color  = color;
    this.hitSet = pierce ? new Set() : null;
    this.dead = false;
  }

  update() {
    this.x += this.vx;
    this.y += this.vy;
    this.life--;
    if (this.life <= 0) this.dead = true;
  }

  draw() {
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
    ctx.fillStyle = this.color;
    ctx.fill();
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.size * 0.45, 0, Math.PI * 2);
    ctx.fillStyle = '#fff';
    ctx.fill();
  }
}

// ── EnemyBullet ────────────────────────────────────────────────────────────
class EnemyBullet {
  constructor(x, y, angle) {
    this.x    = x;
    this.y    = y;
    const spd = 3.5;
    this.vx   = Math.cos(angle) * spd;
    this.vy   = Math.sin(angle) * spd;
    this.r    = 5;
    this.damage = 12;
    this.life   = 99999;
    this.dead   = false;
  }

  update() {
    this.x += this.vx;
    this.y += this.vy;
    this.life--;
    if (this.life <= 0)
      this.dead = true;
  }

  draw() {
    ctx.save();
    ctx.shadowColor = '#e74c3c';
    ctx.shadowBlur  = 8;
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.r, 0, Math.PI * 2);
    ctx.fillStyle = '#c0392b';
    ctx.fill();
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.r * 0.4, 0, Math.PI * 2);
    ctx.fillStyle = '#ff8080';
    ctx.fill();
    ctx.restore();
  }
}

// ── Powerup drops ──────────────────────────────────────────────────────────
const POWERUP_CONFIG = {
  enrage:    { color: '#e74c3c', glow: '#ff6b35', icon: '⚡', label: 'ENRAGE',    duration: 480 }, // 8s
  magnet:    { color: '#9b59b6', glow: '#d7bde2', icon: '✦', label: 'MAGNET',    duration: 360 }, // 6s
  heal:      { color: '#2ecc71', glow: '#27ae60', icon: '♥', label: 'HEAL',      duration: 0   }, // instant
  shield:    { color: '#3498db', glow: '#85c1e9', icon: '◈', label: 'SHIELD',    duration: 240 }, // 4s
  whirlwind: { color: '#f1c40f', glow: '#f39c12', icon: '❋', label: 'WHIRLWIND', duration: 360 }, // 6s
};

const POWERUP_DROP_WEIGHTS = [
  ['heal',      6.0],
  ['whirlwind', 3.0],
  ['enrage',    3.0],
  ['shield',    2.0],
  ['magnet',    0.5],
];
const _DROP_TOTAL = POWERUP_DROP_WEIGHTS.reduce((s, [, w]) => s + w, 0);

function rollPowerupDrop() {
  if (Math.random() > 0.15) return null; // 15% base drop chance
  let r = Math.random() * _DROP_TOTAL;
  for (const [type, w] of POWERUP_DROP_WEIGHTS) {
    r -= w;
    if (r <= 0) return type;
  }
  return 'heal';
}

class Powerup {
  constructor(x, y, type) {
    this.x = x; this.y = y; this.type = type;
    this.r = 10; this.lifetime = 1200; this.pulse = 0; this.dead = false;
  }

  update() { this.pulse += 0.08; if (--this.lifetime <= 0) this.dead = true; }

  draw() {
    const cfg = POWERUP_CONFIG[this.type];
    const p   = Math.sin(this.pulse) * 0.5 + 0.5;
    const r   = this.r + p * 3;
    const alpha = this.lifetime < 180 ? this.lifetime / 180 : 1;

    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.shadowColor = cfg.glow;
    ctx.shadowBlur  = 10 + p * 8;

    // Pentagon body
    ctx.beginPath();
    for (let i = 0; i < 5; i++) {
      const a = (i / 5) * Math.PI * 2 - Math.PI / 2;
      i === 0 ? ctx.moveTo(this.x + Math.cos(a) * r, this.y + Math.sin(a) * r)
              : ctx.lineTo(this.x + Math.cos(a) * r, this.y + Math.sin(a) * r);
    }
    ctx.closePath();
    ctx.fillStyle = cfg.color + '55';
    ctx.fill();
    ctx.strokeStyle = cfg.color;
    ctx.lineWidth = 2;
    ctx.stroke();

    // Icon
    ctx.shadowBlur = 0;
    ctx.fillStyle = '#fff';
    ctx.font = `${11 + p * 2}px Courier New`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(cfg.icon, this.x, this.y);
    ctx.textBaseline = 'alphabetic';

    // Label
    ctx.fillStyle = cfg.color;
    ctx.font = 'bold 9px Courier New';
    ctx.fillText(cfg.label, this.x, this.y - r - 6);
    ctx.restore();
  }
}

// ── WaveManager ────────────────────────────────────────────────────────────
class WaveManager {
  constructor() {
    this.wave             = 0;
    this.spawnTimer       = 0;
    this.waveCleared      = false;
    this.betweenWaveTimer = 0;
    this.roundTimer       = 0;
    this.roundDuration    = 0;
    this.eliteQueue       = []; // frame counts (roundTimer values) at which to spawn an elite
    this.startNextWave();
  }

  get betweenWaves()  { return this.waveCleared; }
  get roundSecsLeft() { return Math.ceil(this.roundTimer / 60); }

  startNextWave() {
    this.wave++;
    this.waveCleared      = false;
    this.spawnTimer       = 0;
    this.betweenWaveTimer = 0;

    // Round duration: 20s wave 1, +7s per wave, cap 90s (reached ~wave 11)
    const secs         = Math.min(90, 20 + (this.wave - 1) * 7);
    this.roundDuration = secs * 60;
    this.roundTimer    = this.roundDuration;

    // Spawn interval: scales with wave and screen size (larger screen = faster spawns)
    const screenScale  = Math.sqrt((W * H) / (900 * 600));
    this.spawnInterval = Math.max(25, Math.round((120 - (this.wave - 1) * 5) / screenScale));

    // Schedule elites evenly through every 3rd round
    this.eliteQueue = [];
    if (this.wave % 3 === 0) {
      const count = Math.floor(this.wave / 3);
      for (let i = 0; i < count; i++) {
        // Spread from 20% to 80% through the round
        const frac = count === 1 ? 0.5 : 0.2 + 0.6 * (i / (count - 1));
        this.eliteQueue.push(Math.floor(this.roundDuration * (1 - frac)));
      }
      // Sort descending so we pop the earliest ones first as timer counts down
      this.eliteQueue.sort((a, b) => b - a);
    }
  }

  _randomType() {
    const w    = this.wave;
    const pool = ['medium', 'medium'];
    // Slow enemies phase out as waves progress
    if (w <= 5) pool.push('slow', 'slow');
    // Fast introduced at wave 3, becomes more common later
    if (w >= 3) pool.push('fast');
    if (w >= 6) pool.push('fast', 'fast');
    // Heavy introduced at wave 4
    if (w >= 4) pool.push('heavy');
    if (w >= 7) pool.push('heavy', 'heavy');
    // Shooter introduced at wave 5
    if (w >= 5) pool.push('shooter');
    if (w >= 9) pool.push('shooter', 'shooter');
    // Charger introduced at wave 6
    if (w >= 6) pool.push('charger');
    if (w >= 10) pool.push('charger', 'charger');
    return pool[Math.floor(Math.random() * pool.length)];
  }

  update(enemies, player) {
    if (this.waveCleared) {
      this.betweenWaveTimer++;
      if (this.betweenWaveTimer > 120) {
        this.betweenWaveTimer = 0;
        this.startNextWave();
      }
      return;
    }

    // Count down round timer
    this.roundTimer--;
    if (this.roundTimer <= 0) {
      this.waveCleared = true;
      return;
    }

    // Spawn scheduled elites
    while (this.eliteQueue.length > 0 &&
           this.roundTimer <= this.eliteQueue[this.eliteQueue.length - 1]) {
      this.eliteQueue.pop();
      const epos = randEdgePos(player.x, player.y);
      enemies.push(new Enemy(epos.x, epos.y, 'elite', this.wave));
    }

    // Continuously spawn regular enemies
    this.spawnTimer++;
    if (this.spawnTimer >= this.spawnInterval) {
      this.spawnTimer = 0;
      // Scale batch size with screen area so density feels consistent at any resolution
      const densityFactor = Math.sqrt((W * H) / (900 * 600));
      const baseBatch = 1 + Math.floor((this.wave - 1) / 4);
      const batch = Math.min(10, Math.round(baseBatch * densityFactor));
      for (let i = 0; i < batch; i++) {
        const pos = randEdgePos(player.x, player.y);
        enemies.push(new Enemy(pos.x, pos.y, this._randomType(), this.wave));
      }
    }
  }
}

// ── Items ───────────────────────────────────────────────────────────────────
const ITEMS = [
  {
    id: 'iron_fortress', name: 'Iron Fortress', icon: '🏛',
    desc: '+50 Max HP  ·  +6 Armor',
    tradeoff: '−0.8 Movement Speed',
    apply(p) { p.maxHp += 50; p.hp = Math.min(p.hp + 50, p.maxHp); p.armor += 6; p.moveSpeed = Math.max(0.5, p.moveSpeed - 0.8); },
  },
  {
    id: 'berserkers_rage', name: "Berserker's Rage", icon: '⚔',
    desc: 'Damage ×1.5  ·  Fire rate +20%',
    tradeoff: 'Armor −8 (take more damage)',
    apply(p) { p.bulletDamage *= 1.5; p.fireCooldownBonus += Math.floor(p.baseFireCD * 0.2); p.armor -= 8; },
  },
  {
    id: 'ghost_shroud', name: 'Ghost Shroud', icon: '👻',
    desc: '+25% Evasion  ·  +0.8 Move Speed',
    tradeoff: '−25 Max HP',
    apply(p) { p.evasion = Math.min(0.75, p.evasion + 0.25); p.moveSpeed += 0.8; p.maxHp = Math.max(20, p.maxHp - 25); p.hp = Math.min(p.hp, p.maxHp); },
  },
  {
    id: 'eagle_eye', name: 'Eagle Eye', icon: '🎯',
    desc: '+30% Crit Chance  ·  Bullet range ×1.6',
    tradeoff: 'Fire rate −10 frames slower',
    apply(p) { p.critChance = Math.min(0.75, p.critChance + 0.30); p.bulletLifetime = Math.floor(p.bulletLifetime * 1.6); p.fireCooldownBonus -= 10; },
  },
  {
    id: 'vampiric_fang', name: 'Vampiric Fang', icon: '🦷',
    desc: '+20% Lifesteal  ·  +0.06 Regen/frame',
    tradeoff: 'Bullet damage −30%',
    apply(p) { p.lifesteal = Math.min(0.60, p.lifesteal + 0.20); p.regenRate += 0.06; p.bulletDamage = Math.max(0.1, p.bulletDamage * 0.7); },
  },
  {
    id: 'cluster_rounds', name: 'Cluster Rounds', icon: '💥',
    desc: 'Bullet size +5  ·  Grants Pierce',
    tradeoff: 'Fire rate −12 frames slower',
    apply(p) { p.bulletSize += 5; p.pierce = true; p.fireCooldownBonus -= 12; },
  },
  {
    id: 'quicksilver', name: 'Quicksilver', icon: '⚡',
    desc: '+2.0 Move Speed  ·  +15% Evasion',
    tradeoff: 'Armor −5',
    apply(p) { p.moveSpeed += 2.0; p.evasion = Math.min(0.75, p.evasion + 0.15); p.armor -= 5; },
  },
  {
    id: 'thornmail', name: 'Thornmail', icon: '🌵',
    desc: '+2.0 Thorns/frame  ·  +5 Armor',
    tradeoff: 'Regen −0.06/frame',
    apply(p) { p.thorns += 2.0; p.armor += 5; p.regenRate = Math.max(0, p.regenRate - 0.06); },
  },
  {
    id: 'deaths_gambit', name: "Death's Gambit", icon: '💀',
    desc: 'Damage ×2.0  ·  +20% Crit Chance',
    tradeoff: 'Max HP halved',
    apply(p) { p.bulletDamage *= 2.0; p.critChance = Math.min(0.75, p.critChance + 0.20); p.maxHp = Math.max(20, Math.floor(p.maxHp / 2)); p.hp = Math.min(p.hp, p.maxHp); },
  },
  {
    id: 'philosophers_stone', name: "Philosopher's Stone", icon: '🔮',
    desc: 'Luck +3  ·  Regen +0.08/frame',
    tradeoff: 'Bullet damage −20%',
    apply(p) { p.luck = Math.min(5, p.luck + 3); p.regenRate += 0.08; p.bulletDamage = Math.max(0.1, p.bulletDamage * 0.8); },
  },
];

// ── Chest (rare floor drop) ─────────────────────────────────────────────────
class Chest {
  constructor(x, y) {
    this.x = x; this.y = y; this.r = 14;
    this.lifetime = 1800; this.pulse = 0; this.dead = false;
  }

  update() { this.pulse += 0.06; if (--this.lifetime <= 0) this.dead = true; }

  draw() {
    const p    = Math.sin(this.pulse) * 0.5 + 0.5;
    const fade = this.lifetime < 180 ? this.lifetime / 180 : 1;
    const w = 26, h = 20;

    ctx.save();
    ctx.globalAlpha = fade;
    ctx.shadowColor = '#f1c40f';
    ctx.shadowBlur  = 10 + p * 10;

    // Body
    ctx.fillStyle = '#7d6608';
    roundRect(ctx, this.x - w / 2, this.y - h / 2 + 4, w, h - 4, 3);
    ctx.fill();
    // Lid
    ctx.fillStyle = '#9a7d0a';
    roundRect(ctx, this.x - w / 2, this.y - h / 2, w, 8, 3);
    ctx.fill();
    // Gold trim
    ctx.strokeStyle = '#f1c40f';
    ctx.lineWidth = 1.5;
    roundRect(ctx, this.x - w / 2, this.y - h / 2, w, h, 3);
    ctx.stroke();
    // Latch
    ctx.beginPath();
    ctx.arc(this.x, this.y + 4, 3, 0, Math.PI * 2);
    ctx.fillStyle = '#f1c40f';
    ctx.fill();

    ctx.shadowBlur = 0;
    ctx.fillStyle = '#f1c40f';
    ctx.font = 'bold 9px Courier New';
    ctx.textAlign = 'center';
    ctx.fillText('CHEST', this.x, this.y - h / 2 - 5);
    ctx.restore();
  }
}

// ── Particles ──────────────────────────────────────────────────────────────
class Particle {
  constructor(x, y, color) {
    this.x = x;
    this.y = y;
    this.vx = (Math.random() - 0.5) * 4;
    this.vy = (Math.random() - 0.5) * 4;
    this.life = 20 + Math.random() * 20;
    this.maxLife = this.life;
    this.r = 2 + Math.random() * 3;
    this.color = color;
    this.dead = false;
  }

  update() {
    this.x += this.vx;
    this.y += this.vy;
    this.vx *= 0.92;
    this.vy *= 0.92;
    this.life--;
    if (this.life <= 0) this.dead = true;
  }

  draw() {
    ctx.globalAlpha = this.life / this.maxLife;
    ctx.beginPath();
    ctx.arc(this.x, this.y, this.r, 0, Math.PI * 2);
    ctx.fillStyle = this.color;
    ctx.fill();
    ctx.globalAlpha = 1;
  }
}

// ── XpOrb ──────────────────────────────────────────────────────────────────
class XpOrb {
  constructor(x, y, value, color, glow) {
    this.x = x;
    this.y = y;
    this.value = value;
    this.color = color;
    this.glow  = glow;
    this.lifetime = 2400;
    this.dead = false;
  }

  update() {
    this.lifetime--;
    if (this.lifetime <= 0) this.dead = true;
  }

  draw() {
    const alpha = this.lifetime < 180 ? this.lifetime / 180 : 1;
    ctx.save();
    ctx.globalAlpha = alpha;
    ctx.shadowColor = this.glow;
    ctx.shadowBlur = 8;
    ctx.beginPath();
    ctx.arc(this.x, this.y, 5, 0, Math.PI * 2);
    ctx.fillStyle = this.color;
    ctx.fill();
    ctx.strokeStyle = this.glow;
    ctx.lineWidth = 1.5;
    ctx.stroke();
    // White center dot
    ctx.shadowBlur = 0;
    ctx.beginPath();
    ctx.arc(this.x, this.y, 2, 0, Math.PI * 2);
    ctx.fillStyle = '#fff';
    ctx.fill();
    ctx.restore();
  }
}

// ── EliteOrb ───────────────────────────────────────────────────────────────
class EliteOrb {
  constructor(x, y) {
    this.x = x;
    this.y = y;
    this.pulse = 0;
    this.dead = false;
  }

  update() {
    this.pulse += 0.07;
  }

  draw() {
    const glow = 12 + Math.sin(this.pulse) * 6;
    ctx.save();
    ctx.shadowColor = '#f1c40f';
    ctx.shadowBlur = glow;
    ctx.beginPath();
    ctx.arc(this.x, this.y, 9, 0, Math.PI * 2);
    ctx.fillStyle = '#9a7d0a';
    ctx.fill();
    ctx.strokeStyle = '#f1c40f';
    ctx.lineWidth = 2.5;
    ctx.stroke();
    ctx.shadowBlur = 0;
    // White center
    ctx.beginPath();
    ctx.arc(this.x, this.y, 3, 0, Math.PI * 2);
    ctx.fillStyle = '#fff';
    ctx.fill();
    // Label
    ctx.fillStyle = '#f1c40f';
    ctx.font = 'bold 8px Courier New';
    ctx.textAlign = 'center';
    ctx.fillText('★', this.x, this.y - 14);
    ctx.restore();
  }
}

// ── XP values & orb colors per enemy type ─────────────────────────────────
// Color reflects XP value: green=1, blue=2, gold=4
const XP_ORB_CONFIG = {
  slow:   { value: 1, color: '#27ae60', glow: '#2ecc71' }, // green  – 1 XP
  medium: { value: 2, color: '#1a6da8', glow: '#3498db' }, // blue   – 2 XP
  fast:   { value: 2, color: '#1a6da8', glow: '#3498db' }, // blue   – 2 XP
  heavy:   { value: 4, color: '#b7770d', glow: '#f1c40f' }, // gold   – 4 XP
  charger: { value: 3, color: '#c0392b', glow: '#ff6b35' }, // red    – 3 XP
  shooter: { value: 3, color: '#117a65', glow: '#1abc9c' }, // teal   – 3 XP
};

// ── Rarity system ──────────────────────────────────────────────────────────
const RARITY = {
  common:   { label: 'Common',   color: '#95a5a6' },
  uncommon: { label: 'Uncommon', color: '#2ecc71' },
  rare:     { label: 'Rare',     color: '#c39bd3' },
};

function rollRarity(luck) {
  // luck 0 → 5% rare, 20% uncommon, 75% common
  // luck 5 → 50% rare, 40% uncommon, 10% common
  const rareChance     = Math.min(0.50, 0.05 + luck * 0.09);
  const uncommonChance = Math.min(0.45, 0.20 + luck * 0.05);
  const roll = Math.random();
  if (roll < rareChance)                  return 'rare';
  if (roll < rareChance + uncommonChance) return 'uncommon';
  return 'common';
}

// ── Upgrade pool ───────────────────────────────────────────────────────────
// tiers define the effect value per rarity; descFn and apply receive that value
const UPGRADES = [
  {
    id: 'fire_speed', name: 'Rapid Fire', icon: '⚡',
    tiers: { common: 2, uncommon: 3, rare: 5 },
    descFn: (v) => `Fire cooldown –${v} frames`,
    maxed: (p) => p.fireCooldownBonus >= Math.floor(p.baseFireCD * 0.4),
    apply(p, v) { p.fireCooldownBonus += v; },
  },
  {
    id: 'bullet_size', name: 'Big Shot', icon: '●',
    tiers: { common: 1, uncommon: 2, rare: 3 },
    descFn: (v) => `Projectile radius +${v}`,
    maxed: (p) => p.bulletSize >= p.weapon.maxBulletSize,
    apply(p, v) { p.bulletSize = Math.min(p.bulletSize + v, p.weapon.maxBulletSize); },
  },
  {
    id: 'damage', name: 'Heavy Rounds', icon: '💥',
    tiers: { common: 0.5, uncommon: 1, rare: 2 },
    descFn: (v) => `Bullet damage +${v}`,
    maxed: (p) => p.bulletDamage >= p.weapon.maxDamage,
    apply(p, v) { p.bulletDamage = Math.min(p.bulletDamage + v, p.weapon.maxDamage); },
  },
  {
    id: 'move_speed', name: 'Afterburner', icon: '▶▶',
    tiers: { common: 0.5, uncommon: 1.0, rare: 1.8 },
    descFn: (v) => `Movement speed +${v.toFixed(1)}`,
    maxed: (p) => p.moveSpeed >= 6,
    apply(p, v) { p.moveSpeed = Math.min(p.moveSpeed + v, 6); },
  },
  {
    id: 'health', name: 'Reinforced Hull', icon: '♥',
    tiers: { common: 20, uncommon: 40, rare: 70 },
    descFn: (v) => `Max HP +${v}  ·  heal ${v}`,
    maxed: (_p) => false, // HP has no cap
    apply(p, v) { p.maxHp += v; p.hp = Math.min(p.hp + v, p.maxHp); },
  },
  {
    id: 'regen', name: 'Nanobots', icon: '↺',
    tiers: { common: 0.04, uncommon: 0.08, rare: 0.14 },
    descFn: (v) => `Regen +${v.toFixed(2)} HP/frame`,
    maxed: (p) => p.regenRate >= 0.20,
    apply(p, v) { p.regenRate = Math.min(p.regenRate + v, 0.20); },
  },
  {
    id: 'range', name: 'Extended Range', icon: '↔',
    tiers: { common: 15, uncommon: 25, rare: 40 },
    descFn: (v) => `Bullet range +${v} frames`,
    maxed: (p) => p.bulletLifetime >= p.weapon.maxBulletLifetime,
    apply(p, v) { p.bulletLifetime = Math.min(p.bulletLifetime + v, p.weapon.maxBulletLifetime); },
  },
  {
    id: 'evasion', name: 'Evasion', icon: '◌',
    tiers: { common: 0.05, uncommon: 0.08, rare: 0.15 },
    descFn: (v) => `Dodge chance +${Math.round(v * 100)}%`,
    maxed: (p) => p.evasion >= 0.40,
    apply(p, v) { p.evasion = Math.min(p.evasion + v, 0.40); },
  },
  {
    id: 'lifesteal', name: 'Lifesteal', icon: '♦',
    tiers: { common: 0.03, uncommon: 0.06, rare: 0.12 },
    descFn: (v) => `Lifesteal +${Math.round(v * 100)}%`,
    maxed: (p) => p.lifesteal >= 0.30,
    apply(p, v) { p.lifesteal = Math.min(p.lifesteal + v, 0.30); },
  },
  {
    id: 'armor', name: 'Armor', icon: '▣',
    tiers: { common: 2, uncommon: 4, rare: 7 },
    descFn: (v) => `Armor +${v} (flat dmg reduction)`,
    maxed: (p) => p.armor >= 15,
    apply(p, v) { p.armor = Math.min(p.armor + v, 15); },
  },
  {
    id: 'crit', name: 'Critical Hit', icon: '✕',
    tiers: { common: 0.05, uncommon: 0.08, rare: 0.15 },
    descFn: (v) => `Crit chance +${Math.round(v * 100)}%  (×2 dmg)`,
    maxed: (p) => p.critChance >= 0.40,
    apply(p, v) { p.critChance = Math.min(p.critChance + v, 0.40); },
  },
  {
    id: 'thorns', name: 'Thorns', icon: '❖',
    tiers: { common: 0.3, uncommon: 0.6, rare: 1.0 },
    descFn: (v) => `Thorns +${v} dmg/frame to touchers`,
    maxed: (p) => p.thorns >= 3.0,
    apply(p, v) { p.thorns = Math.min(p.thorns + v, 3.0); },
  },
  {
    id: 'luck', name: 'Lucky Break', icon: '★',
    tiers: { common: 1, uncommon: 1, rare: 2 },
    descFn: (v) => `Luck +${v} — better upgrade odds`,
    maxed: (p) => p.luck >= 5,
    apply(p, v) { p.luck = Math.min(p.luck + v, 5); },
  },
];

// ── Weapons ────────────────────────────────────────────────────────────────
const WEAPONS = [
  {
    id: 'pistol', name: 'Pistol', color: '#ffe082',
    tag: 'Balanced all-rounder',
    statA: 'CD: 18  ·  DMG: 1.2',
    statB: 'Medium range',
    fireCD: 18, damage: 1.2, bulletSize: 4, pierce: false,
    burstCount: 1, spreadAngle: 0, bulletSpeed: BULLET_SPEED,
    bulletLifetime: 100, maxBulletLifetime: 200,
    maxDamage: 5,   maxBulletSize: 8,
  },
  {
    id: 'smg', name: 'SMG', color: '#82e0aa',
    tag: 'Fast fire, short range',
    statA: 'CD: 8  ·  DMG: 0.5',
    statB: 'Short range',
    fireCD: 8, damage: 0.5, bulletSize: 3, pierce: false,
    burstCount: 1, spreadAngle: 0, bulletSpeed: BULLET_SPEED,
    bulletLifetime: 55, maxBulletLifetime: 110,
    maxDamage: 2.5, maxBulletSize: 6,
  },
  {
    id: 'shotgun', name: 'Shotgun', color: '#f0b27a',
    tag: '5-pellet burst, close range',
    statA: 'CD: 42  ·  DMG: 1×5',
    statB: 'Very short range',
    fireCD: 42, damage: 1, bulletSize: 3, pierce: false,
    burstCount: 5, spreadAngle: 0.20, bulletSpeed: BULLET_SPEED,
    bulletLifetime: 38, maxBulletLifetime: 76,
    maxDamage: 2.5, maxBulletSize: 6,
  },
  {
    id: 'sniper', name: 'Sniper', color: '#85c1e9',
    tag: 'Slow, high DMG, pierce',
    statA: 'CD: 55  ·  DMG: 4',
    statB: 'Very long range, pierce',
    fireCD: 55, damage: 4, bulletSize: 3, pierce: true,
    burstCount: 1, spreadAngle: 0, bulletSpeed: BULLET_SPEED * 2.2,
    bulletLifetime: 85, maxBulletLifetime: 170,
    maxDamage: 9,   maxBulletSize: 5,
  },
  {
    id: 'cannon', name: 'Cannon', color: '#c39bd3',
    tag: 'Huge slow slug, pierce',
    statA: 'CD: 60  ·  DMG: 4',
    statB: 'Medium range, pierce',
    fireCD: 60, damage: 4, bulletSize: 10, pierce: true,
    burstCount: 1, spreadAngle: 0, bulletSpeed: BULLET_SPEED * 0.6,
    bulletLifetime: 130, maxBulletLifetime: 260,
    maxDamage: 9,   maxBulletSize: 14,
  },
];

// ── Game ───────────────────────────────────────────────────────────────────
const Game = {
  state: STATES.MENU,
  player: null,
  enemies: [],
  bullets: [],
  particles: [],
  xpOrbs: [],
  eliteOrbs: [],
  waveManager: null,
  score: 0,
  finalWave: 1,
  levelUpChoices: [],
  levelUpReason: 'level',   // 'level' | 'wave'
  waveUpgradeForWave: 0,    // tracks which wave already gave its upgrade
  showStats: false,
  selectedWeaponIndex: 0,

  init() {
    this.player         = new Player(WEAPONS[this.selectedWeaponIndex]);
    this.enemies        = [];
    this.bullets        = [];
    this.particles      = [];
    this.xpOrbs         = [];
    this.eliteOrbs      = [];
    this.enemyBullets   = [];
    this.powerups       = [];
    this.chests         = [];
    this.waveManager    = new WaveManager();
    this.score          = 0;
    this.scores         = Leaderboard.load();
    this.levelUpChoices    = [];
    this.levelUpReason     = 'level';
    this.waveUpgradeForWave = 0;
    this.showStats         = false;
  },

  start() {
    this.init();
    this.state = STATES.PLAYING;
  },

  _pickItems(n) {
    const available = ITEMS.filter(it => !this.player.ownedItems.has(it.id));
    const shuffled  = available.sort(() => Math.random() - 0.5);
    return shuffled.slice(0, n).map(it => ({ ...it, _isItem: true }));
  },

  _pickUpgrades(n) {
    const available = UPGRADES.filter(u => !u.maxed(this.player));
    const shuffled  = available.sort(() => Math.random() - 0.5);
    return shuffled.slice(0, n).map(upg => {
      const rarityKey  = rollRarity(this.player.luck);
      const rarityInfo = RARITY[rarityKey];
      const amount     = upg.tiers[rarityKey];
      return {
        ...upg,
        rarityKey,
        rarityLabel: rarityInfo.label,
        rarityColor: rarityInfo.color,
        amount,
        desc: upg.descFn(amount),
      };
    });
  },

  applyUpgrade(index) {
    const upgrade = this.levelUpChoices[index];
    if (!upgrade) return;
    if (upgrade._isItem) {
      upgrade.apply(this.player);
      this.player.ownedItems.add(upgrade.id);
    } else {
      upgrade.apply(this.player, upgrade.amount);
    }
    this.player.levelUpFlash = 60;
    this.levelUpChoices = [];
    this.state = STATES.PLAYING;
  },

  update() {
    if (this.state !== STATES.PLAYING) return;

    this.player.update(this.enemies);
    this.player.tryFire(this.bullets);

    const wasPlaying = !this.waveManager.betweenWaves;
    this.waveManager.update(this.enemies, this.player);

    // Round just ended — sweep remaining enemies off the field
    if (wasPlaying && this.waveManager.betweenWaves) {
      const deathColors = { slow: '#ff6666', medium: '#f0a050', fast: '#c39bd3', heavy: '#2e86c1', elite: '#f1c40f' };
      for (const e of this.enemies) {
        for (let i = 0; i < 8; i++) {
          this.particles.push(new Particle(e.x, e.y, deathColors[e.type] || '#aaa'));
        }
      }
      this.enemies = [];
    }

    // Update enemies
    for (const e of this.enemies) {
      e.update(this.player, this.enemyBullets);
      // Enemy↔player collision — damage scales 10% per wave
      if (dist(e, this.player) < e.r + this.player.r) {
        this.player.takeDamage(ENEMY_DAMAGE * (1 + (this.waveManager.wave - 1) * 0.06));
        if (this.player.thorns > 0) e.hp -= this.player.thorns;
      }
    }

    // Update enemy bullets and check player hit
    for (const b of this.enemyBullets) {
      b.update();
      if (!b.dead && dist(b, this.player) < b.r + this.player.r) {
        this.player.takeDamage(b.damage);
        b.dead = true;
      }
    }
    this.enemyBullets = this.enemyBullets.filter(b => !b.dead && dist(b, this.player) < Math.max(W, H) * 2);

    // Update bullets
    for (const b of this.bullets) b.update();

    // Bullet↔enemy collision
    for (const b of this.bullets) {
      if (b.dead) continue;
      for (const e of this.enemies) {
        if (b.hitSet && b.hitSet.has(e)) continue; // pierce: skip already-hit
        if (dist(b, e) < e.r + b.size) {
          e.hp -= b.damage;
          if (this.player.lifesteal > 0)
            this.player.hp = Math.min(this.player.maxHp, this.player.hp + b.damage * this.player.lifesteal);
          for (let i = 0; i < 5; i++) {
            this.particles.push(new Particle(b.x, b.y, b.color));
          }
          if (b.pierce) {
            b.hitSet.add(e); // keep going through enemies
          } else {
            b.dead = true;
            break;
          }
        }
      }
    }

    // Remove dead enemies, spawn death particles and orbs
    this.enemies = this.enemies.filter(e => {
      if (e.hp <= 0) {
        const colors = { slow: '#ff6666', medium: '#f0a050', fast: '#c39bd3', heavy: '#2e86c1', elite: '#f1c40f' };
        const count  = e.type === 'elite' ? 20 : 10;
        for (let i = 0; i < count; i++) {
          this.particles.push(new Particle(e.x, e.y, colors[e.type]));
        }
        if (e.type === 'elite') {
          this.eliteOrbs.push(new EliteOrb(e.x, e.y));
        } else {
          const cfg = XP_ORB_CONFIG[e.type];
          this.xpOrbs.push(new XpOrb(e.x, e.y, cfg.value, cfg.color, cfg.glow));
          const drop = rollPowerupDrop();
          if (drop) this.powerups.push(new Powerup(e.x, e.y, drop));
          if (Math.random() < 0.03) this.chests.push(new Chest(e.x + 20, e.y));
        }
        this.score++;
        return false;
      }
      return true;
    });

    // Remove dead bullets
    this.bullets = this.bullets.filter(b => !b.dead);

    // Update XP orbs and check pickup
    const magnetActive = this.player.hasPowerup('magnet');
    for (const orb of this.xpOrbs) {
      orb.update();
      if (!orb.dead) {
        if (magnetActive) {
          const d = dist(orb, this.player);
          if (d > 1) {
            const a = Math.atan2(this.player.y - orb.y, this.player.x - orb.x);
            orb.x += Math.cos(a) * Math.min(14, d);
            orb.y += Math.sin(a) * Math.min(14, d);
          }
        }
        if (dist(orb, this.player) < XP_PICKUP_RADIUS) {
          this.player.addXP(orb.value);
          orb.dead = true;
        }
      }
    }
    this.xpOrbs = this.xpOrbs.filter(o => !o.dead);

    // Update elite orbs — collecting one opens the upgrade screen
    for (const orb of this.eliteOrbs) {
      orb.update();
      if (!orb.dead && dist(orb, this.player) < XP_PICKUP_RADIUS) {
        orb.dead = true;
        this.levelUpReason  = 'elite';
        this.levelUpChoices = this._pickUpgrades(3);
        this.state = STATES.LEVEL_UP;
        break;
      }
    }
    this.eliteOrbs = this.eliteOrbs.filter(o => !o.dead);

    // Update floor powerups and check pickup
    for (const pu of this.powerups) {
      pu.update();
      if (!pu.dead && dist(pu, this.player) < XP_PICKUP_RADIUS + 4) {
        this.player.activatePowerup(pu.type);
        pu.dead = true;
      }
    }
    this.powerups = this.powerups.filter(p => !p.dead);

    // Update chests and check pickup
    for (const ch of this.chests) {
      ch.update();
      if (!ch.dead && dist(ch, this.player) < ch.r + this.player.r) {
        ch.dead = true;
        const choices = this._pickItems(3);
        if (choices.length > 0) {
          this.levelUpReason  = 'chest';
          this.levelUpChoices = choices;
          this.state = STATES.LEVEL_UP;
        }
      }
    }
    this.chests = this.chests.filter(c => !c.dead);

    // Update particles
    for (const p of this.particles) p.update();
    this.particles = this.particles.filter(p => !p.dead);

    // Check level-up
    if (this.player.pendingLevelUp) {
      this.player.pendingLevelUp = false;
      this.levelUpReason  = 'level';
      this.levelUpChoices = this._pickUpgrades(3);
      this.state = STATES.LEVEL_UP;
      return;
    }

    // Check wave-clear upgrade (once per wave, shown as soon as wave ends)
    if (this.waveManager.betweenWaves &&
        this.waveManager.wave !== this.waveUpgradeForWave) {
      this.waveUpgradeForWave = this.waveManager.wave;
      this.levelUpReason  = 'wave';
      this.levelUpChoices = this._pickUpgrades(3);
      this.state = STATES.LEVEL_UP;
      return;
    }

    // Check game over
    if (this.player.hp <= 0) {
      this.player.hp = 0;
      this.finalWave = this.waveManager.wave;
      this.scores    = Leaderboard.save(this.score, this.finalWave, this.player.level);
      this.state = STATES.GAME_OVER;
    }
  },

  draw() {
    // ── Screen-space background (scrolling infinite grid) ──────────────────
    ctx.fillStyle = '#0d0d18';
    ctx.fillRect(0, 0, W, H);

    if (this.state === STATES.MENU) { this.drawMenu(); return; }
    if (this.state === STATES.GAME_OVER) { this.drawGameOver(); return; }

    // Scrolling grid — offset by camera so it tiles infinitely
    const GRID = 40;
    const camX = this.player.x - W / 2;
    const camY = this.player.y - H / 2;
    const offX = ((camX % GRID) + GRID) % GRID;
    const offY = ((camY % GRID) + GRID) % GRID;
    ctx.strokeStyle = 'rgba(255,255,255,0.04)';
    ctx.lineWidth = 1;
    for (let x = -offX; x <= W + GRID; x += GRID) {
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, H); ctx.stroke();
    }
    for (let y = -offY; y <= H + GRID; y += GRID) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }

    // ── World-space rendering (camera-transformed) ─────────────────────────
    ctx.save();
    ctx.translate(-camX, -camY);

    for (const p of this.particles) p.draw();
    for (const o of this.xpOrbs)    o.draw();
    for (const o of this.eliteOrbs) o.draw();
    for (const p of this.powerups)  p.draw();
    for (const c of this.chests)    c.draw();
    for (const b of this.bullets)       b.draw();
    for (const b of this.enemyBullets)  b.draw();
    for (const e of this.enemies)       e.draw();
    this.player.draw();

    ctx.restore();

    // ── Screen-space UI ────────────────────────────────────────────────────
    this.drawHUD();
    if (this.showStats) this.drawStatsPanel();

    if (this.state === STATES.LEVEL_UP) {
      this.drawLevelUp();
      return;
    }

    // Between-wave banner
    if (this.waveManager.betweenWaves) {
      ctx.fillStyle = 'rgba(0,0,0,0.5)';
      ctx.fillRect(0, H / 2 - 40, W, 80);
      ctx.fillStyle = '#f1c40f';
      ctx.font = 'bold 32px Courier New';
      ctx.textAlign = 'center';
      ctx.fillText(`Round ${this.waveManager.wave - 1} complete!  Next round incoming…`, W / 2, H / 2 + 10);
    }
  },

  drawHUD() {
    // Wave
    ctx.fillStyle = '#ecf0f1';
    ctx.font = 'bold 18px Courier New';
    ctx.textAlign = 'left';
    ctx.fillText(`Round ${this.waveManager.wave}`, 14, 24);

    // Countdown timer
    const secsLeft = this.waveManager.betweenWaves ? 0 : this.waveManager.roundSecsLeft;
    const timerColor = secsLeft <= 10 ? '#e74c3c' : '#ecf0f1';
    ctx.fillStyle = timerColor;
    ctx.fillText(`${secsLeft}s left`, 14, 46);

    // Round progress bar (thin, under the text)
    const tbw = 100, tbh = 4, tbx = 14, tby = 52;
    const timeRatio = this.waveManager.betweenWaves ? 0
      : this.waveManager.roundTimer / this.waveManager.roundDuration;
    ctx.fillStyle = '#333';
    ctx.fillRect(tbx, tby, tbw, tbh);
    ctx.fillStyle = secsLeft <= 10 ? '#e74c3c' : '#2ecc71';
    ctx.fillRect(tbx, tby, tbw * timeRatio, tbh);

    // Score + Level (top-right)
    ctx.textAlign = 'right';
    ctx.fillText(`Kills: ${this.score}`, W - 14, 24);
    ctx.fillText(`Lvl: ${this.player.level}`, W - 14, 46);

    // Stats hint
    ctx.fillStyle = '#555';
    ctx.font = '11px Courier New';
    ctx.fillText('[Tab] Stats', W - 14, H - 10);

    // Player HP bar (top-center)
    const bw = 200, bh = 14;
    const bx = W / 2 - bw / 2;
    const by = 10;
    ctx.fillStyle = '#1a1a2e';
    ctx.fillRect(bx - 1, by - 1, bw + 2, bh + 2);
    ctx.fillStyle = '#333';
    ctx.fillRect(bx, by, bw, bh);
    const hpRatio = this.player.hp / this.player.maxHp;
    ctx.fillStyle = hpRatio > 0.5 ? '#2ecc71' : hpRatio > 0.25 ? '#f39c12' : '#e74c3c';
    ctx.fillRect(bx, by, bw * hpRatio, bh);
    ctx.strokeStyle = '#555';
    ctx.lineWidth = 1;
    ctx.strokeRect(bx, by, bw, bh);
    ctx.fillStyle = '#fff';
    ctx.font = '11px Courier New';
    ctx.textAlign = 'center';
    ctx.fillText(`HP  ${Math.ceil(this.player.hp)} / ${this.player.maxHp}`, W / 2, by + 10);

    // XP bar (just below HP bar)
    const xby = by + bh + 4;
    const xbh = 8;
    ctx.fillStyle = '#1a1a2e';
    ctx.fillRect(bx - 1, xby - 1, bw + 2, xbh + 2);
    ctx.fillStyle = '#333';
    ctx.fillRect(bx, xby, bw, xbh);
    const xpRatio = this.player.xp / this.player.xpToNext;
    ctx.fillStyle = '#9b59b6';
    ctx.fillRect(bx, xby, bw * xpRatio, xbh);
    ctx.strokeStyle = '#555';
    ctx.lineWidth = 1;
    ctx.strokeRect(bx, xby, bw, xbh);
    ctx.fillStyle = '#fff';
    ctx.font = '9px Courier New';
    ctx.textAlign = 'left';
    ctx.fillText(`LVL ${this.player.level}`, bx + 3, xby + 7);
    ctx.textAlign = 'center';
    ctx.fillText(`XP ${this.player.xp}/${this.player.xpToNext}`, W / 2, xby + 7);

    // Active powerup icons (below XP bar)
    const active = Object.entries(this.player.activePowerups);
    if (active.length > 0) {
      const iconW = 48, iconH = 42, gap = 6;
      const totalW = active.length * iconW + (active.length - 1) * gap;
      let ix = W / 2 - totalW / 2;
      const iy = xby + xbh + 8;
      for (const [type, timer] of active) {
        const cfg = POWERUP_CONFIG[type];
        const ratio = timer / cfg.duration;
        // Box
        ctx.fillStyle = 'rgba(0,0,0,0.6)';
        roundRect(ctx, ix, iy, iconW, iconH, 4); ctx.fill();
        ctx.strokeStyle = cfg.color;
        ctx.lineWidth = 1.5;
        roundRect(ctx, ix, iy, iconW, iconH, 4); ctx.stroke();
        // Icon + label
        ctx.fillStyle = cfg.color;
        ctx.font = 'bold 14px Courier New';
        ctx.textAlign = 'center';
        ctx.fillText(cfg.icon, ix + iconW / 2, iy + 18);
        ctx.font = '8px Courier New';
        ctx.fillText(cfg.label, ix + iconW / 2, iy + 28);
        // Timer bar
        ctx.fillStyle = '#333';
        ctx.fillRect(ix + 4, iy + iconH - 7, iconW - 8, 4);
        ctx.fillStyle = cfg.color;
        ctx.fillRect(ix + 4, iy + iconH - 7, (iconW - 8) * ratio, 4);
        ix += iconW + gap;
      }
    }
  },

  drawStatsPanel() {
    const p = this.player;
    const pw = 220, pad = 16;
    const px = W - pw - 10;
    const rows = [
      { label: 'Weapon',      value: p.weapon.name },
      { label: 'Level',       value: p.level },
      { label: 'Luck',        value: `${p.luck} / 5` },
      null,
      { label: 'Move Speed',  value: p.moveSpeed.toFixed(1) },
      { label: 'Fire Cooldown', value: `${p.fireCooldownMax} frames` },
      { label: 'Damage',      value: p.bulletDamage },
      { label: 'Proj. Size',  value: p.bulletSize },
      { label: 'Range',       value: `${p.bulletLifetime} frames` },
      { label: 'Pierce',      value: p.pierce ? 'Yes' : 'No' },
      { label: 'Regen',       value: `${p.regenRate.toFixed(2)} HP/f` },
      { label: 'Evasion',     value: `${Math.round(p.evasion * 100)}%` },
      { label: 'Armor',       value: p.armor },
      { label: 'Crit Chance', value: `${Math.round(p.critChance * 100)}%` },
      { label: 'Lifesteal',   value: `${Math.round(p.lifesteal * 100)}%` },
      { label: 'Thorns',      value: `${p.thorns.toFixed(1)} dmg/f` },
      null,
      { label: 'HP',          value: `${Math.ceil(p.hp)} / ${p.maxHp}` },
    ];

    const rowH = 22;
    const panelH = pad * 2 + rows.length * rowH;
    const py = H / 2 - panelH / 2;

    // Background
    ctx.fillStyle = 'rgba(10,10,24,0.88)';
    roundRect(ctx, px, py, pw, panelH, 8);
    ctx.fill();
    ctx.strokeStyle = '#444';
    ctx.lineWidth = 1;
    roundRect(ctx, px, py, pw, panelH, 8);
    ctx.stroke();

    // Title
    ctx.fillStyle = '#f1c40f';
    ctx.font = 'bold 13px Courier New';
    ctx.textAlign = 'center';
    ctx.fillText('PLAYER STATS', px + pw / 2, py + pad + 4);

    // Rows
    let ry = py + pad + rowH;
    for (const row of rows) {
      if (row === null) { ry += rowH * 0.4; continue; }
      ctx.fillStyle = '#7f8c8d';
      ctx.font = '12px Courier New';
      ctx.textAlign = 'left';
      ctx.fillText(row.label, px + pad, ry);
      ctx.fillStyle = '#ecf0f1';
      ctx.textAlign = 'right';
      ctx.fillText(row.value, px + pw - pad, ry);
      ry += rowH;
    }
  },

  drawLevelUp() {
    // Dim background
    ctx.fillStyle = 'rgba(0,0,0,0.72)';
    ctx.fillRect(0, 0, W, H);

    // Title
    ctx.textAlign = 'center';
    const isWave  = this.levelUpReason === 'wave';
    const isElite = this.levelUpReason === 'elite';
    const isChest = this.levelUpReason === 'chest';
    const titleText = isElite ? 'ELITE DEFEATED!'
      : isWave  ? `ROUND ${this.waveManager.wave - 1} COMPLETE!`
      : isChest ? '📦  CHEST FOUND!'
      : `LEVEL UP!  →  ${this.player.level}`;
    const titleColor = isElite ? '#f1c40f' : isWave ? '#2ecc71' : isChest ? '#e67e22' : '#f1c40f';

    ctx.fillStyle = titleColor;
    ctx.font = 'bold 38px Courier New';
    ctx.shadowColor = titleColor;
    ctx.shadowBlur = 16;
    ctx.fillText(titleText, W / 2, 130);
    ctx.shadowBlur = 0;

    ctx.fillStyle = '#bdc3c7';
    ctx.font = '15px Courier New';
    ctx.fillText('Choose an upgrade  (click or press 1 / 2 / 3)', W / 2, 162);

    // Cards
    const cardW = 220, cardH = 200, gap = 24;
    const totalW = 3 * cardW + 2 * gap;
    const startX = (W - totalW) / 2;
    const cardY = 195;

    const cardH = isChest ? 230 : 200;
    this.levelUpChoices.forEach((upg, i) => {
      const cx  = startX + i * (cardW + gap);
      const rc  = upg._isItem ? '#e67e22' : upg.rarityColor;
      const glow = upg._isItem ? 12 : upg.rarityKey === 'rare' ? 16 : upg.rarityKey === 'uncommon' ? 8 : 0;

      // Card background
      ctx.fillStyle = '#1a1a2e';
      roundRect(ctx, cx, cardY, cardW, cardH, 10);
      ctx.fill();

      // Border
      ctx.shadowColor = rc;
      ctx.shadowBlur  = glow;
      ctx.strokeStyle = rc;
      ctx.lineWidth   = upg._isItem || upg.rarityKey === 'rare' ? 3 : 2;
      roundRect(ctx, cx, cardY, cardW, cardH, 10);
      ctx.stroke();
      ctx.shadowBlur = 0;

      // Badge (top-right)
      ctx.fillStyle = rc;
      ctx.font = 'bold 11px Courier New';
      ctx.textAlign = 'right';
      ctx.fillText(upg._isItem ? 'ITEM' : upg.rarityLabel.toUpperCase(), cx + cardW - 10, cardY + 18);

      // Number badge (top-left)
      ctx.fillStyle = '#ecf0f1';
      ctx.textAlign = 'left';
      ctx.fillText(`[${i + 1}]`, cx + 10, cardY + 18);

      // Icon
      ctx.font = '34px Courier New';
      ctx.textAlign = 'center';
      ctx.fillText(upg.icon, cx + cardW / 2, cardY + 75);

      // Name
      ctx.fillStyle = rc;
      ctx.font = 'bold 15px Courier New';
      ctx.fillText(upg.name, cx + cardW / 2, cardY + 112);

      // Desc
      ctx.fillStyle = '#bdc3c7';
      ctx.font = '12px Courier New';
      wrapText(upg.desc, cx + cardW / 2, cardY + 133, cardW - 20, 16);

      // Tradeoff (items only)
      if (upg._isItem && upg.tradeoff) {
        ctx.fillStyle = '#e74c3c';
        ctx.font = '11px Courier New';
        wrapText('⚠ ' + upg.tradeoff, cx + cardW / 2, cardY + 188, cardW - 20, 15);
      }

      upg._cardX = cx; upg._cardY = cardY; upg._cardW = cardW; upg._cardH = cardH;
    });
  },

  drawMenu() {
    ctx.fillStyle = 'rgba(0,0,0,0.6)';
    ctx.fillRect(0, 0, W, H);
    ctx.textAlign = 'center';

    // Title
    ctx.fillStyle = '#c0392b';
    ctx.font = 'bold 52px Courier New';
    ctx.fillText('SURVIVE', W / 2, 72);
    ctx.fillStyle = '#e74c3c';
    ctx.font = 'bold 24px Courier New';
    ctx.fillText('THE  HORDE', W / 2, 103);

    ctx.fillStyle = '#95a5a6';
    ctx.font = '13px Courier New';
    ctx.fillText('WASD / Arrows to move  ·  Auto-fire at nearest enemy', W / 2, 128);

    // Weapon select label
    ctx.fillStyle = '#f1c40f';
    ctx.font = 'bold 14px Courier New';
    ctx.fillText('SELECT WEAPON', W / 2, 155);

    // Weapon cards
    const cw = 154, ch = 105, gap = 10;
    const totalW = WEAPONS.length * cw + (WEAPONS.length - 1) * gap;
    const sx = (W - totalW) / 2;
    const cy = 165;

    WEAPONS.forEach((wpn, i) => {
      const cx = sx + i * (cw + gap);
      const selected = i === this.selectedWeaponIndex;
      const inner = cw - 16; // usable text width with 8px padding each side
      const mid   = cx + cw / 2;

      // Background
      ctx.fillStyle = selected ? 'rgba(40,40,80,0.95)' : 'rgba(20,20,40,0.85)';
      roundRect(ctx, cx, cy, cw, ch, 8);
      ctx.fill();

      // Border
      ctx.shadowColor = wpn.color;
      ctx.shadowBlur  = selected ? 14 : 0;
      ctx.strokeStyle = selected ? wpn.color : '#444';
      ctx.lineWidth   = selected ? 2.5 : 1;
      roundRect(ctx, cx, cy, cw, ch, 8);
      ctx.stroke();
      ctx.shadowBlur = 0;

      // Weapon name
      ctx.fillStyle = selected ? wpn.color : '#ecf0f1';
      ctx.font = 'bold 13px Courier New';
      ctx.textAlign = 'center';
      ctx.fillText(wpn.name, mid, cy + 20);

      // Tag — wrap within inner width
      ctx.fillStyle = '#95a5a6';
      ctx.font = '10px Courier New';
      wrapText(wpn.tag, mid, cy + 36, inner, 13);

      // Stat lines (already short enough)
      ctx.fillStyle = '#bdc3c7';
      ctx.font = '10px Courier New';
      ctx.fillText(wpn.statA, mid, cy + 66);
      ctx.fillStyle = wpn.pierce ? '#c39bd3' : '#7f8c8d';
      ctx.fillText(wpn.statB, mid, cy + 80);

      // Store bounds for click detection
      wpn._mx = cx; wpn._my = cy; wpn._mw = cw; wpn._mh = ch;
    });

    // Controls hint
    ctx.fillStyle = '#7f8c8d';
    ctx.font = '12px Courier New';
    ctx.fillText('WASD / Arrows to move  ·  Tab = Stats panel  ·  1/2/3 = pick upgrade', W / 2, 286);

    // Start button
    ctx.fillStyle = '#27ae60';
    roundRect(ctx, W / 2 - 110, 308, 220, 50, 8);
    ctx.fill();
    ctx.fillStyle = '#fff';
    ctx.font = 'bold 20px Courier New';
    ctx.fillText('START GAME', W / 2, 339);

    ctx.fillStyle = '#7f8c8d';
    ctx.font = '13px Courier New';
    ctx.fillText('or press  ENTER', W / 2, 378);
  },

  drawGameOver() {
    ctx.fillStyle = 'rgba(0,0,0,0.82)';
    ctx.fillRect(0, 0, W, H);

    const isNewBest = this.scores.length > 0 && this.scores[0].score === this.score;

    // ── Left column: run summary ──────────────────────────────────────────
    const lx = 225; // center of left column
    ctx.textAlign = 'center';

    ctx.fillStyle = '#e74c3c';
    ctx.font = 'bold 52px Courier New';
    ctx.shadowColor = '#e74c3c';
    ctx.shadowBlur = 14;
    ctx.fillText('GAME OVER', lx, 110);
    ctx.shadowBlur = 0;

    if (isNewBest) {
      ctx.fillStyle = '#f1c40f';
      ctx.font = 'bold 16px Courier New';
      ctx.shadowColor = '#f1c40f';
      ctx.shadowBlur = 10;
      ctx.fillText('★  NEW HIGH SCORE  ★', lx, 140);
      ctx.shadowBlur = 0;
    }

    ctx.fillStyle = '#ecf0f1';
    ctx.font = '20px Courier New';
    ctx.fillText(`Round ${this.finalWave}`, lx, 185);
    ctx.fillText(`Kills: ${this.score}`, lx, 215);
    ctx.fillText(`Level: ${this.player.level}`, lx, 245);

    // Divider
    ctx.strokeStyle = '#444';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(40, 268); ctx.lineTo(410, 268);
    ctx.stroke();

    // Weapon used
    ctx.fillStyle = '#7f8c8d';
    ctx.font = '15px Courier New';
    ctx.fillText(`Weapon: ${this.player.weapon.name}`, lx, 292);

    // Play again button
    ctx.fillStyle = '#2980b9';
    roundRect(ctx, lx - 110, 330, 220, 48, 8);
    ctx.fill();
    ctx.fillStyle = '#fff';
    ctx.font = 'bold 20px Courier New';
    ctx.fillText('PLAY AGAIN', lx, 361);

    ctx.fillStyle = '#7f8c8d';
    ctx.font = '13px Courier New';
    ctx.fillText('or press ENTER', lx, 406);

    // ── Right column: leaderboard ─────────────────────────────────────────
    const rx = 670; // center of right column
    const lbX = 460, lbW = 420;

    // Panel background
    ctx.fillStyle = 'rgba(15,15,30,0.9)';
    roundRect(ctx, lbX, 30, lbW, 540, 8);
    ctx.fill();
    ctx.strokeStyle = '#333';
    ctx.lineWidth = 1;
    roundRect(ctx, lbX, 30, lbW, 540, 8);
    ctx.stroke();

    // Title
    ctx.fillStyle = '#f1c40f';
    ctx.font = 'bold 18px Courier New';
    ctx.textAlign = 'center';
    ctx.fillText('LEADERBOARD', rx, 65);

    // Column headers
    ctx.fillStyle = '#7f8c8d';
    ctx.font = '12px Courier New';
    ctx.textAlign = 'left';
    ctx.fillText('#', lbX + 16, 94);
    ctx.fillText('Kills', lbX + 46, 94);
    ctx.fillText('Rnd', lbX + 150, 94);
    ctx.fillText('Lvl', lbX + 220, 94);
    ctx.fillText('Date', lbX + 290, 94);

    ctx.strokeStyle = '#333';
    ctx.beginPath();
    ctx.moveTo(lbX + 12, 100); ctx.lineTo(lbX + lbW - 12, 100);
    ctx.stroke();

    // Entries
    const entries = this.scores;
    const rowH = 46;
    for (let i = 0; i < Math.min(entries.length, 10); i++) {
      const e  = entries[i];
      const ey = 110 + i * rowH;
      const isThisRun = e.score === this.score && e.wave === this.finalWave && e.level === this.player.level && i === entries.findIndex(x => x.score === this.score);

      // Highlight current run
      if (isThisRun) {
        ctx.fillStyle = 'rgba(241,196,15,0.12)';
        roundRect(ctx, lbX + 8, ey - 14, lbW - 16, rowH - 4, 4);
        ctx.fill();
      }

      // Rank medal for top 3
      const rankColors = ['#f1c40f', '#bdc3c7', '#cd7f32'];
      ctx.fillStyle = i < 3 ? rankColors[i] : '#7f8c8d';
      ctx.font = i < 3 ? 'bold 14px Courier New' : '13px Courier New';
      ctx.textAlign = 'left';
      ctx.fillText(`${i + 1}`, lbX + 16, ey + 4);

      ctx.fillStyle = isThisRun ? '#f1c40f' : '#ecf0f1';
      ctx.font = isThisRun ? 'bold 15px Courier New' : '14px Courier New';
      ctx.fillText(`${e.score}`, lbX + 46, ey + 4);

      ctx.fillStyle = '#bdc3c7';
      ctx.font = '13px Courier New';
      ctx.fillText(`${e.wave}`, lbX + 150, ey + 4);
      ctx.fillText(`${e.level}`, lbX + 220, ey + 4);
      ctx.fillStyle = '#7f8c8d';
      ctx.fillText(`${e.date}`, lbX + 290, ey + 4);

      if (i < 9 && i < entries.length - 1) {
        ctx.strokeStyle = 'rgba(255,255,255,0.06)';
        ctx.beginPath();
        ctx.moveTo(lbX + 12, ey + rowH - 6); ctx.lineTo(lbX + lbW - 12, ey + rowH - 6);
        ctx.stroke();
      }
    }

    if (entries.length === 0) {
      ctx.fillStyle = '#555';
      ctx.font = '14px Courier New';
      ctx.textAlign = 'center';
      ctx.fillText('No scores yet', rx, 200);
    }
  },

  handleClick(mx, my) {
    if (this.state === STATES.MENU) {
      // Weapon cards
      WEAPONS.forEach((wpn, i) => {
        if (wpn._mx !== undefined &&
            mx >= wpn._mx && mx <= wpn._mx + wpn._mw &&
            my >= wpn._my && my <= wpn._my + wpn._mh) {
          this.selectedWeaponIndex = i;
        }
      });
      // Start button
      if (mx >= W / 2 - 110 && mx <= W / 2 + 110 && my >= 308 && my <= 358) {
        this.start();
      }
    } else if (this.state === STATES.GAME_OVER) {
      if (mx >= 115 && mx <= 335 && my >= 330 && my <= 378) {
        this.state = STATES.MENU;
      }
    } else if (this.state === STATES.LEVEL_UP) {
      this.levelUpChoices.forEach((upg, i) => {
        if (upg._cardX !== undefined &&
            mx >= upg._cardX && mx <= upg._cardX + upg._cardW &&
            my >= upg._cardY && my <= upg._cardY + upg._cardH) {
          this.applyUpgrade(i);
        }
      });
    }
  },
};

// ── Helper: wrap text ──────────────────────────────────────────────────────
// Draws text word-wrapped within maxW, centered on x. Returns final y.
function wrapText(text, x, y, maxW, lineH) {
  const words = text.split(' ');
  let line = '';
  for (const word of words) {
    const test = line ? line + ' ' + word : word;
    if (ctx.measureText(test).width > maxW && line) {
      ctx.fillText(line, x, y);
      line = word;
      y += lineH;
    } else {
      line = test;
    }
  }
  ctx.fillText(line, x, y);
  return y;
}

// ── Helper: rounded rect ───────────────────────────────────────────────────
function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.lineTo(x + w - r, y);
  ctx.quadraticCurveTo(x + w, y, x + w, y + r);
  ctx.lineTo(x + w, y + h - r);
  ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  ctx.lineTo(x + r, y + h);
  ctx.quadraticCurveTo(x, y + h, x, y + h - r);
  ctx.lineTo(x, y + r);
  ctx.quadraticCurveTo(x, y, x + r, y);
  ctx.closePath();
}

// ── Input: Enter key ───────────────────────────────────────────────────────
window.addEventListener('keydown', e => {
  if (e.key === 'Enter') {
    if (Game.state === STATES.MENU || Game.state === STATES.GAME_OVER) {
      Game.start();
    }
  }
  if (e.key === 'Tab') {
    e.preventDefault();
    if (Game.state === STATES.PLAYING || Game.state === STATES.LEVEL_UP) {
      Game.showStats = !Game.showStats;
    }
  }
  if (Game.state === STATES.LEVEL_UP) {
    if (e.key === '1') Game.applyUpgrade(0);
    if (e.key === '2') Game.applyUpgrade(1);
    if (e.key === '3') Game.applyUpgrade(2);
  }
});

// ── Input: Mouse click ─────────────────────────────────────────────────────
canvas.addEventListener('click', e => {
  const rect = canvas.getBoundingClientRect();
  const mx = e.clientX - rect.left;
  const my = e.clientY - rect.top;
  Game.handleClick(mx, my);
});

// ── Main loop ──────────────────────────────────────────────────────────────
function loop() {
  Game.update();
  Game.draw();
  requestAnimationFrame(loop);
}

loop();
