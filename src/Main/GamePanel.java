package Main;

import javax.swing.JPanel;

import Entity.Bullet;
import Entity.Enemy;
import Entity.Player;
import PowerUps.PowerUp;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.ArrayList;

@SuppressWarnings("serial")
public class GamePanel extends JPanel implements Runnable, KeyListener{
	
	//FIELDS
	public static int WIDTH = 400;
	public static int HEIGHT = 400;
	
	private Thread thread;
	private boolean running;
	
	private BufferedImage image;
	private Graphics2D g;
	
	private int FPS = 30;
	private double averageFPS;
	
	public static Player player;
	public static ArrayList<Bullet> bullets;
	public static ArrayList<Enemy> enemies;
	public static ArrayList<PowerUp> powerups;
	
	private long waveStartTimer;
	private long waveStartTimerDiff;
	private int waveNumber;
	private boolean waveStart;
	private int waveDelay = 2000;
	
	//CONSTRUCTOR
	public GamePanel() {
		super();
		setPreferredSize(new Dimension(WIDTH,HEIGHT));
		setFocusable(true);
		requestFocus();
	}
	
	//FUNCTIONS
	public void addNotify() {
		super.addNotify();
		if(thread == null) {
			thread = new Thread(this);
			thread.start();
		}
		addKeyListener(this);
	}
	
	public void run() {
		running = true;
		
		image = new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);
		g = (Graphics2D) image.getGraphics();
		
		//Set antialiasing for the graphics
		g.setRenderingHint(
				RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		
		//Set antialiasing for the text
		g.setRenderingHint(
				RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		
		//Initialize ArrayList's
		player = new Player();
		bullets = new ArrayList<Bullet>();
		enemies = new ArrayList<Enemy>();
		powerups = new ArrayList<PowerUp>();
		
		waveStartTimer = 0;
		waveStartTimerDiff = 0;
		waveStart = true;
		waveNumber = 0;
		
		long startTime;
		long URDTimeMillis;
		long waitTime;
		long totalTime = 0;
		
		int frameCount = 0;
		int maxFrameCount = 30;
		
		//Will give amount of time the loop has to run to maintain 30 FPS
		long targetTime = 1000 / FPS;
		
		//GAME LOOP
		while(running) {
			
			startTime = System.nanoTime();
			
			gameUpdate();
			gameRender();
			gameDraw();
			
			//Get time in milliseconds
			URDTimeMillis = (System.nanoTime() - startTime) / 1000000;
			
			//Each loop has to be 33 milliseconds and this will set that
			waitTime = targetTime - URDTimeMillis;
			
			//This try catch will run if loop draws in less then 33 milliseconds
			try {
				Thread.sleep(waitTime);
			}
			catch(Exception e) {
				e.printStackTrace();
			}
			
			totalTime += System.nanoTime() - startTime;
			frameCount++;
			if(frameCount == maxFrameCount) {
				averageFPS = 1000.0 / ((totalTime / frameCount) / 1000000);
				frameCount = 0;
				totalTime = 0;
			}	
		}
	}
	
	private void gameUpdate() {
		
		//New wave
		if(waveStartTimer == 0 && enemies.size() == 0) {
			waveNumber++;
			waveStart = false;
			waveStartTimer = System.nanoTime();
		}
		else {
			waveStartTimerDiff = (System.nanoTime() - waveStartTimer) / 1000000;
			if(waveStartTimerDiff > waveDelay) {
				waveStart = true;
				waveStartTimer = 0;
				waveStartTimerDiff = 0;
			}
		}
		
		//Create enemies
		if(waveStart && enemies.size() == 0) {
			createNewEnemies();
		}
		
		//Player update
		player.update();
		
		//Bullet update
		for(int i = 0; i < bullets.size(); i++) {
			boolean remove = bullets.get(i).update();
			if(remove) {
				bullets.remove(i);
				i--;
			}
		}
		
		//Enemy update
		for(int i = 0; i < enemies.size(); i++) {
			enemies.get(i).update();
		}
		
		//Powerup update
		for(int i = 0; i < powerups.size(); i++) {
			boolean remove = powerups.get(i).update();
			if(remove) {
				powerups.remove(i);
				i--;
			}
		}
		
		//Bullet-enemy collision
		for(int i = 0; i < bullets.size(); i++) {
			
			Bullet b = bullets.get(i);
			double bx = b.getx();
			double by = b.gety();
			double br = b.getr();
			
			for(int j = 0; j < enemies.size(); j++) {
				
				Enemy e = enemies.get(j);
				double ex = e.getx();
				double ey = e.gety();
				double er = e.getr();
				
				double dx = bx - ex;
				double dy = by - ey;
				double dist = Math.sqrt(dx * dx + dy * dy);
				
				if(dist < br + er) {
					//Enemy gets hit
					e.hit();
					
					//Remove bullet that hit the enemy
					bullets.remove(i);
					i--;
					break;
				}
				
			}
		}
		
		//Check dead enemies
		for(int i = 0; i < enemies.size(); i++) {
			if(enemies.get(i).isDead()) {	
				//Add to score when killing an enemy
				Enemy e = enemies.get(i);
				
				//Chance for powerup
				double rand = Math.random();
				if(rand < 0.001) powerups.add(new PowerUp(1, e.getx(), e.gety()));
				else if(rand < 0.020) powerups.add(new PowerUp(3, e.getx() , e.gety()));
				else if(rand < 0.120) powerups.add(new PowerUp(2, e.getx(), e.gety()));
				
				//score  = sum of type and rank
				player.addScore(e.getType() + e.getRank());
				
				//Remove dead enemies
				enemies.remove(i);
				i--;
				
				e.explode();
			}
		}
		
		//Player-enemy collision
		if(!player.isRecovering()) {
			
			int px = player.getx();
			int py = player.gety();
			int pr = player.getr();
			for(int i = 0; i < enemies.size(); i++) {
				Enemy e = enemies.get(i);
				double ex = e.getx();
				double ey = e.gety();
				double er = e.getr();
				
				double dx = px - ex;
				double dy = py - ey;
				double dist = Math.sqrt(dx * dx + dy * dy);
				
				if(dist < pr + er) {
					player.loseLife();
				}
			}
		}
		
		//Player-powerup collision
		int px = player.getx();
		int py = player.gety();
		int pr = player.getr();
		
		for(int i = 0; i < powerups.size(); i++) {
			PowerUp p = powerups.get(i);
			double x = p.getx();
			double y = p.gety();
			double r = p.getr();
			double dx = px - x;
			double dy = py - y;
			double dist = Math.sqrt(dx * dx + dy + dy);
			
			//Collected powerups
			if(dist < pr + r) {
				
				int type = p.getType();
				
				//Extra life
				if(type == 1) {
					player.gainLife();
				}
				if(type == 2) {
					player.increasedPower(1);
				}
				if(type == 3) {
					player.increasedPower(2);
				}
				
				powerups.remove(i);
				i--;
			}
		}
    }
	
	private void gameRender() {
		
		//Draw Background
		g.setColor(new Color(0,100,255));
		g.fillRect(0, 0, WIDTH, HEIGHT);
		
		//Draw Player
		player.draw(g);
		
		//Draw Bullets
		for(int i = 0; i < bullets.size(); i++) {
			bullets.get(i).draw(g);
		}
		
		//Draw Enemy 
		for(int i = 0; i < enemies.size(); i++) {
			enemies.get(i).draw(g);
		}
		
		//Draw Powerups
		for(int i = 0; i < powerups.size(); i++) {
			powerups.get(i).draw(g);
		}
		
		//Draw wave number;
		if(waveStartTimer != 0) {
			g.setFont(new Font("Century Gothic", Font.PLAIN, 18));
			String s = "- W A V E    " + waveNumber + "    -";
			int length = (int) g.getFontMetrics().getStringBounds(s, g).getWidth();
			int alpha = (int) (255 * Math.sin(3.14 * waveStartTimerDiff / waveDelay));
			if(alpha > 255) alpha = 255;
			g.setColor(new Color(255,255,255,alpha));
			g.drawString(s, WIDTH / 2 - length / 2, HEIGHT / 2);
		}
		
		//Draw player lives
		for(int i = 0; i < player.getLives(); i++) {
			g.setColor(Color.WHITE);
			g.fillOval(20 + (20 * i), 20, player.getr() * 2, player.getr() * 2);
			g.setStroke(new BasicStroke(3));
			g.setColor(Color.WHITE.darker());
			g.drawOval(20 + (20 * i), 20, player.getr() * 2, player.getr() * 2);
			g.setStroke(new BasicStroke(1));
		}
		
		//Draw player power
		g.setColor(Color.YELLOW);
		g.fillRect(20, 40, player.getPower() * 8, 8);
		g.setColor(Color.YELLOW.darker());
		g.setStroke(new BasicStroke(2));
		for(int i = 0; i < player.getRequiredPower(); i++) {
			g.drawRect(20 + 8 * i, 40, 8, 8);
		}
		g.setStroke(new BasicStroke(1));
		
		//Draw player score
		g.setColor(Color.WHITE);
		g.setFont(new Font("Century Gothic", Font.PLAIN, 14));
		g.drawString("Score: " + player.getScore(), WIDTH - 100, 30);
	}
	
	private void gameDraw() {
		Graphics g2 = this.getGraphics();
		g2.drawImage(image, 0, 0, null);
		g2.dispose();
	}
	
	private void createNewEnemies() {
		//Create new waves
		enemies.clear();
		Enemy e;
		
		//Wave 1
		if(waveNumber == 1) {
			for(int i = 0; i < 4; i++) {
				enemies.add(new Enemy(1, 1));
			}
		}
		//Wave 2
		if(waveNumber == 2) {
			for(int i = 0; i < 4; i++) {
				enemies.add(new Enemy(1, 1));
			}
			enemies.add(new Enemy(1, 2));
			enemies.add(new Enemy(1, 2));
		}
		//Wave 3
		if(waveNumber == 3) {
			enemies.add(new Enemy(1, 3));
			enemies.add(new Enemy(1, 3));
			enemies.add(new Enemy(1, 4));
		}
	}
	
	public void keyTyped(KeyEvent key) {}
	
	//Override function for when the keys are pressed down
	@Override
	public void keyPressed(KeyEvent key) {
		int keyCode = key.getKeyCode();
		if(keyCode == KeyEvent.VK_LEFT) {
			player.setLeft(true);
		}
		if(keyCode == KeyEvent.VK_RIGHT) {
			player.setRight(true);
		}
		if(keyCode == KeyEvent.VK_UP) {
			player.setUp(true);
		}
		if(keyCode == KeyEvent.VK_DOWN) {
			player.setDown(true);
		}
		if(keyCode == KeyEvent.VK_Z) {
			player.setFiring(true);
		}
	}
	
	//Override function for when the keys are released
	@Override
	public void keyReleased(KeyEvent key) {
		int keyCode = key.getKeyCode();
		if(keyCode == KeyEvent.VK_LEFT) {
			player.setLeft(false);
		}
		if(keyCode == KeyEvent.VK_RIGHT) {
			player.setRight(false);
		}
		if(keyCode == KeyEvent.VK_UP) {
			player.setUp(false);
		}
		if(keyCode == KeyEvent.VK_DOWN) {
			player.setDown(false);
		}
		if(keyCode == KeyEvent.VK_Z) {
			player.setFiring(false);
		}
	}
}























