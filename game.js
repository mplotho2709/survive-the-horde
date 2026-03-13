// ── Constants ──────────────────────────────────────────────────────────────
const W = 900;
const H = 600;
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

// ── Canvas setup ───────────────────────────────────────────────────────────
const canvas = document.getElementById('gameCanvas');
const ctx = canvas.getContext('2d');
canvas.width  = W;
canvas.height = H;

// ── Input ──────────────────────────────────────────────────────────────────
const keys = {};
window.addEventListener('keydown', e => { keys[e.key] = true; });
window.addEventListener('keyup',   e => { keys[e.key] = false; });

// ── Utility ────────────────────────────────────────────────────────────────
function dist(a, b) {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function randEdgePos() {
  const side = Math.floor(Math.random() * 4);
  switch (side) {
    case 0: return { x: Math.random() * W, y: -20 };
    case 1: return { x: W + 20,            y: Math.random() * H };
    case 2: return { x: Math.random() * W, y: H + 20 };
    default: return { x: -20,              y: Math.random() * H };
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
  }

  get fireCooldownMax() {
    const maxBonus = Math.floor(this.baseFireCD * 0.4);
    return Math.max(4, this.baseFireCD - Math.min(this.fireCooldownBonus, maxBonus));
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
    this.x = Math.max(this.r, Math.min(W - this.r, this.x + dx * this.moveSpeed));
    this.y = Math.max(this.r, Math.min(H - this.r, this.y + dy * this.moveSpeed));

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
  }

  tryFire(bullets) {
    if (this.fireCooldown === 0 && this.closestEnemy) {
      for (let i = 0; i < this.burstCount; i++) {
        const spread = (i - (this.burstCount - 1) / 2) * this.spreadAngle;
        bullets.push(new Bullet(
          this.x, this.y,
          this.aimAngle + spread,
          this.bulletDamage, this.bulletSize,
          this.pierce, this.bulletSpeed, this.bulletColor, this.bulletLifetime
        ));
      }
      this.fireCooldown = this.fireCooldownMax;
    }
  }

  draw() {
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
    // HP grows 22% per wave; speed grows 6% per wave (elites scale harder)
    const hpScale    = type === 'elite' ? 1 + (wave - 1) * 0.35 : 1 + (wave - 1) * 0.22;
    const speedScale = 1 + (wave - 1) * 0.06;
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
    this.x = Math.max(this.r, Math.min(W - this.r, this.x));
    this.y = Math.max(this.r, Math.min(H - this.r, this.y));

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
      this.x = Math.max(this.r, Math.min(W - this.r, this.x));
      this.y = Math.max(this.r, Math.min(H - this.r, this.y));
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
    this.life   = 180;
    this.dead   = false;
  }

  update() {
    this.x += this.vx;
    this.y += this.vy;
    this.life--;
    if (this.life <= 0 || this.x < -20 || this.x > W + 20 || this.y < -20 || this.y > H + 20)
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

    // Round duration: 30s for round 1, +10s per round, max 120s
    const secs         = Math.min(120, 30 + (this.wave - 1) * 10);
    this.roundDuration = secs * 60;
    this.roundTimer    = this.roundDuration;

    // Spawn interval: 90 frames → 20 frames minimum
    this.spawnInterval = Math.max(20, 90 - (this.wave - 1) * 8);

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
    const pool = ['medium', 'medium', 'medium'];
    if (w <= 4) pool.push('slow', 'slow');
    if (w >= 2) pool.push('fast', 'fast');
    if (w >= 3) pool.push('heavy', 'shooter');
    if (w >= 4) pool.push('charger');
    if (w >= 5) pool.push('heavy', 'fast');
    if (w >= 6) pool.push('charger');
    if (w >= 7) pool.push('heavy', 'heavy');
    if (w >= 9) pool.push('charger', 'charger');
    return pool[Math.floor(Math.random() * pool.length)];
  }

  update(enemies) {
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
      enemies.push(new Enemy(randEdgePos().x, randEdgePos().y, 'elite', this.wave));
    }

    // Continuously spawn regular enemies
    this.spawnTimer++;
    if (this.spawnTimer >= this.spawnInterval) {
      this.spawnTimer = 0;
      const batch = Math.min(6, 1 + Math.floor(this.wave * 0.5));
      for (let i = 0; i < batch; i++) {
        const pos = randEdgePos();
        enemies.push(new Enemy(pos.x, pos.y, this._randomType(), this.wave));
      }
    }
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
    this.waveManager    = new WaveManager();
    this.score          = 0;
    this.levelUpChoices    = [];
    this.levelUpReason     = 'level';
    this.waveUpgradeForWave = 0;
    this.showStats         = false;
  },

  start() {
    this.init();
    this.state = STATES.PLAYING;
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
    upgrade.apply(this.player, upgrade.amount);
    this.player.levelUpFlash = 60;
    this.levelUpChoices = [];
    this.state = STATES.PLAYING;
  },

  update() {
    if (this.state !== STATES.PLAYING) return;

    this.player.update(this.enemies);
    this.player.tryFire(this.bullets);

    const wasPlaying = !this.waveManager.betweenWaves;
    this.waveManager.update(this.enemies);

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
        this.player.hp -= ENEMY_DAMAGE * (1 + (this.waveManager.wave - 1) * 0.10);
      }
    }

    // Update enemy bullets and check player hit
    for (const b of this.enemyBullets) {
      b.update();
      if (!b.dead && dist(b, this.player) < b.r + this.player.r) {
        this.player.hp -= b.damage;
        b.dead = true;
      }
    }
    this.enemyBullets = this.enemyBullets.filter(b => !b.dead);

    // Update bullets
    for (const b of this.bullets) b.update();

    // Bullet↔enemy collision
    for (const b of this.bullets) {
      if (b.dead) continue;
      for (const e of this.enemies) {
        if (b.hitSet && b.hitSet.has(e)) continue; // pierce: skip already-hit
        if (dist(b, e) < e.r + b.size) {
          e.hp -= b.damage;
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
        }
        this.score++;
        return false;
      }
      return true;
    });

    // Remove dead bullets
    this.bullets = this.bullets.filter(b => !b.dead);

    // Update XP orbs and check pickup
    const vacuuming = this.waveManager.betweenWaves;
    const VACUUM_SPEED = 14;
    for (const orb of this.xpOrbs) {
      orb.update();
      if (!orb.dead) {
        const d = dist(orb, this.player);
        if (vacuuming && d > 1) {
          const angle = Math.atan2(this.player.y - orb.y, this.player.x - orb.x);
          orb.x += Math.cos(angle) * Math.min(VACUUM_SPEED, d);
          orb.y += Math.sin(angle) * Math.min(VACUUM_SPEED, d);
        }
        if (d < XP_PICKUP_RADIUS || (vacuuming && d < 8)) {
          this.player.addXP(orb.value);
          orb.dead = true;
        }
      }
    }
    this.xpOrbs = this.xpOrbs.filter(o => !o.dead);

    // Update elite orbs — collecting one opens the upgrade screen
    for (const orb of this.eliteOrbs) {
      orb.update();
      if (!orb.dead) {
        const d = dist(orb, this.player);
        if (vacuuming && d > 1) {
          const angle = Math.atan2(this.player.y - orb.y, this.player.x - orb.x);
          orb.x += Math.cos(angle) * Math.min(VACUUM_SPEED, d);
          orb.y += Math.sin(angle) * Math.min(VACUUM_SPEED, d);
        }
        if (d < XP_PICKUP_RADIUS || (vacuuming && d < 8)) {
          orb.dead = true;
          this.levelUpReason  = 'elite';
          this.levelUpChoices = this._pickUpgrades(3);
          this.state = STATES.LEVEL_UP;
          break;
        }
      }
    }
    this.eliteOrbs = this.eliteOrbs.filter(o => !o.dead);

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
      this.state = STATES.GAME_OVER;
    }
  },

  draw() {
    // Background
    ctx.fillStyle = '#0d0d18';
    ctx.fillRect(0, 0, W, H);

    // Grid
    ctx.strokeStyle = 'rgba(255,255,255,0.04)';
    ctx.lineWidth = 1;
    for (let x = 0; x < W; x += 40) {
      ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, H); ctx.stroke();
    }
    for (let y = 0; y < H; y += 40) {
      ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke();
    }

    if (this.state === STATES.MENU) {
      this.drawMenu();
      return;
    }

    if (this.state === STATES.GAME_OVER) {
      this.drawGameOver();
      return;
    }

    // Playing (also shown behind LEVEL_UP overlay)
    for (const p of this.particles) p.draw();
    for (const o of this.xpOrbs)    o.draw();
    for (const o of this.eliteOrbs) o.draw();
    for (const b of this.bullets)        b.draw();
    for (const b of this.enemyBullets)   b.draw();
    for (const e of this.enemies)        e.draw();
    this.player.draw();
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
    const titleText = isElite ? 'ELITE DEFEATED!'
      : isWave  ? `ROUND ${this.waveManager.wave - 1} COMPLETE!`
      : `LEVEL UP!  →  ${this.player.level}`;
    const titleColor = isElite ? '#f1c40f' : isWave ? '#2ecc71' : '#f1c40f';

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

    this.levelUpChoices.forEach((upg, i) => {
      const cx = startX + i * (cardW + gap);
      const rc = upg.rarityColor;

      // Card background
      ctx.fillStyle = '#1a1a2e';
      roundRect(ctx, cx, cardY, cardW, cardH, 10);
      ctx.fill();

      // Rarity-colored border (thicker for rare)
      ctx.shadowColor = rc;
      ctx.shadowBlur  = upg.rarityKey === 'rare' ? 16 : upg.rarityKey === 'uncommon' ? 8 : 0;
      ctx.strokeStyle = rc;
      ctx.lineWidth   = upg.rarityKey === 'rare' ? 3 : 2;
      roundRect(ctx, cx, cardY, cardW, cardH, 10);
      ctx.stroke();
      ctx.shadowBlur = 0;

      // Rarity badge (top-right)
      ctx.fillStyle = rc;
      ctx.font = 'bold 11px Courier New';
      ctx.textAlign = 'right';
      ctx.fillText(upg.rarityLabel.toUpperCase(), cx + cardW - 10, cardY + 18);

      // Number badge (top-left)
      ctx.fillStyle = '#ecf0f1';
      ctx.textAlign = 'left';
      ctx.fillText(`[${i + 1}]`, cx + 10, cardY + 18);

      // Icon
      ctx.font = '34px Courier New';
      ctx.textAlign = 'center';
      ctx.fillText(upg.icon, cx + cardW / 2, cardY + 75);

      // Name in rarity color
      ctx.fillStyle = rc;
      ctx.font = 'bold 15px Courier New';
      ctx.fillText(upg.name, cx + cardW / 2, cardY + 112);

      // Desc — wrapped so long text stays inside card
      ctx.fillStyle = '#bdc3c7';
      ctx.font = '12px Courier New';
      wrapText(upg.desc, cx + cardW / 2, cardY + 133, cardW - 20, 16);

      // Store card bounds for click detection
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
    ctx.fillStyle = 'rgba(0,0,0,0.75)';
    ctx.fillRect(0, 0, W, H);

    ctx.textAlign = 'center';

    ctx.fillStyle = '#e74c3c';
    ctx.font = 'bold 60px Courier New';
    ctx.fillText('GAME  OVER', W / 2, 190);

    ctx.fillStyle = '#ecf0f1';
    ctx.font = '24px Courier New';
    ctx.fillText(`You reached Round ${this.finalWave}`, W / 2, 255);
    ctx.fillText(`Total kills: ${this.score}`, W / 2, 290);
    ctx.fillText(`Level: ${this.player.level}`, W / 2, 325);

    // Play again button
    ctx.fillStyle = '#2980b9';
    roundRect(ctx, W / 2 - 120, 370, 240, 52, 8);
    ctx.fill();
    ctx.fillStyle = '#fff';
    ctx.font = 'bold 22px Courier New';
    ctx.fillText('PLAY AGAIN', W / 2, 403);

    ctx.fillStyle = '#7f8c8d';
    ctx.font = '14px Courier New';
    ctx.fillText('or press  ENTER', W / 2, 455);
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
      if (mx >= W / 2 - 120 && mx <= W / 2 + 120 && my >= 370 && my <= 422) {
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
