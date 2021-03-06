package me.archdukeliamus.badui.typewriter;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;

public class MovementListener implements CaretListener, ComponentListener, KeyListener {
	private final float SCALE = Toolkit.getDefaultToolkit().getScreenResolution() / 96.0f;
	private final int VERTICAL_SHIFT = (int) (17 * SCALE); //These only work for 12pt monospaced
	private final int HORIZONTAL_SHIFT = (int) (7 * SCALE);
	private final JFrame frame;
	private final JTextArea area;
	private final Rectangle bounds = getFullBounds();
	
	MovementListener(JFrame frame, JTextArea area) {
		this.frame = frame;
		this.area = area;
		originalLocation = frame.getLocation();
	}
	
	//Thanks Java devs for putting this in javadoc
	private Rectangle getFullBounds() {
		Rectangle virtualBounds = new Rectangle();
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] gs = ge.getScreenDevices();
		for (int j = 0; j < gs.length; j++) {
			GraphicsDevice gd = gs[j];
			GraphicsConfiguration[] gc = gd.getConfigurations();
			for (int i = 0; i < gc.length; i++) {
				virtualBounds = virtualBounds.union(gc[i].getBounds());
			}
		} 
		return virtualBounds;
	}
	
	boolean lockEvents = false;
	
	int lastCaretPosition = 0;
	int lastLineCaretXPosition = 0;
	int lastLineOfCaret = 0;
	String originalString = "";
	Point originalLocation;
	int accumMoveX = 0;
	int accumMoveY = 0;
	
	public void caretUpdate(CaretEvent unused) {
		if (lockEvents) return;
		int newCaretPosition = area.getCaretPosition();
		if (lastCaretPosition == newCaretPosition) return;
		int newLineOfCaret = getCaretY();
		int newLineCaretXPosition = getCaretX();
		
		int newX = frame.getX() - (HORIZONTAL_SHIFT * (newLineCaretXPosition-lastLineCaretXPosition));
		int newY = frame.getY() - (VERTICAL_SHIFT * (newLineOfCaret-lastLineOfCaret));
		
		if (newX < 0 || newX + frame.getWidth() >= bounds.width || newY < 0 || newY + frame.getHeight() >= bounds.height) {
			Toolkit.getDefaultToolkit().beep();
			lockEvents = true;
			SwingUtilities.invokeLater(() -> {
				Toolkit.getDefaultToolkit().beep();
				area.setText(originalString);
				if (lastCaretPosition != newCaretPosition) {
					//events should already be locked at this point
					area.setCaretPosition(lastCaretPosition);
				}
			});
		} else {
			accumMoveX = 0;
			accumMoveY = 0;
			lockEvents = true;
			frame.setLocation(newX,newY-5);
			originalLocation = frame.getLocation();
			lastCaretPosition = newCaretPosition;
			lastLineCaretXPosition = newLineCaretXPosition;
			lastLineOfCaret = newLineOfCaret;
			originalString = area.getText();
			SwingUtilities.invokeLater(() -> {
				try {
					Thread.sleep(25);
				} catch (InterruptedException e) {}
				frame.setLocation(newX,newY);
				try {
					Thread.sleep(25);
				} catch (InterruptedException e) {}
			});
		}
		SwingUtilities.invokeLater(() -> {
			lockEvents = false;
		});
	}
	
	private int getCaretX() {
		try {
			return area.getCaretPosition()-area.getLineStartOffset(area.getLineOfOffset(area.getCaretPosition()));
		} catch (BadLocationException e) {
			return 0;
		}
	}
	
	private int getCaretY() {
		try {
			return area.getLineOfOffset(area.getCaretPosition());
		} catch (BadLocationException e) {
			return 0;
		}
	}

	@Override
	public void componentResized(ComponentEvent e) {}
	
	@Override
	public void componentMoved(ComponentEvent e) {
		if (e.getComponent() == frame) {
			if (lockEvents) return;
			
			Point newLocation = frame.getLocation();
			int deltaX = newLocation.x - originalLocation.x;
			int deltaY = newLocation.y - originalLocation.y;
			
			accumMoveX += deltaX;
			accumMoveY += deltaY;
			
			while (accumMoveX >= HORIZONTAL_SHIFT) {
				accumMoveX -= HORIZONTAL_SHIFT;
				if (getCaretX() > 0) {
					lockEvents = true;
					int newPosition = area.getCaretPosition()-1;
					area.setCaretPosition(newPosition);
					lastCaretPosition = newPosition;
					lastLineCaretXPosition = getCaretX();
					lastLineOfCaret = getCaretY();
				}
			}
			
			while (accumMoveX <= -HORIZONTAL_SHIFT) {
				accumMoveX += HORIZONTAL_SHIFT;
				if (area.getCaretPosition() < getEndOfLineOffset(getCaretY())-1) {
					lockEvents = true;
					int newPosition = area.getCaretPosition()+1;
					area.setCaretPosition(newPosition);
					lastCaretPosition = newPosition;
					lastLineCaretXPosition = getCaretX();
					lastLineOfCaret = getCaretY();
				}
			}
			
			while (accumMoveY >= VERTICAL_SHIFT) {
				accumMoveY -= VERTICAL_SHIFT;
				moveCaretUpLine();
			}	
			while (accumMoveY <= -VERTICAL_SHIFT) {
				accumMoveY += VERTICAL_SHIFT;
				moveCaretDownLine();
			}
			
			originalLocation = newLocation;
			
			SwingUtilities.invokeLater(() -> {
				lockEvents = false;
			});
		}
	}
	
	private int getEndOfLineOffset(int line) {
		try {
			if (line == area.getLineCount()-1) return area.getText().length()+1;
			return area.getLineEndOffset(line);
		} catch (BadLocationException e) {
			return 0;
		}
	}
	
	private void moveCaretUpLine() {
		int start = area.getCaretPosition();
		int currX = getCaretX();
		int currY = getCaretY();
		if (currY == 0) return;
		try {
			int startOffset = area.getLineStartOffset(currY-1);
			int endOffset = area.getLineEndOffset(currY-1);
			if (startOffset + currX >= endOffset) {
				lockEvents = true;
				area.setCaretPosition(endOffset - 1);
			} else {
				lockEvents = true;
				area.setCaretPosition(startOffset + currX);
			}
			if (area.getCaretPosition() != start) {
				lastCaretPosition = area.getCaretPosition();
				lastLineCaretXPosition = getCaretX();
				lastLineOfCaret = getCaretY();
			}
		} catch (BadLocationException e) {
			return;
		}
	}
	
	private void moveCaretDownLine() {
		int start = area.getCaretPosition();
		int currX = getCaretX();
		int currY = getCaretY();
		if (currY == area.getLineCount()-1) {
			if (currX > 0) {
				return;
			} else {
				lockEvents = true;
				area.append("\n");
				area.setCaretPosition(area.getText().length());
				lastCaretPosition = area.getCaretPosition();
				lastLineCaretXPosition = getCaretX();
				lastLineOfCaret = getCaretY();
				return;
				
			}
		}
		try {
			int startOffset = area.getLineStartOffset(currY+1);
			int endOffset = area.getLineEndOffset(currY+1);
			if (startOffset + currX > endOffset) {
				lockEvents = true;
				area.setCaretPosition(endOffset - 1);
			} else {
				lockEvents = true;
				area.setCaretPosition(startOffset + currX);
			}
			if (area.getCaretPosition() != start) {
				lastCaretPosition = area.getCaretPosition();
				lastLineCaretXPosition = getCaretX();
				lastLineOfCaret = getCaretY();
			}
		} catch (BadLocationException e) {
			e.printStackTrace();
			return;
		}
	}

	@Override
	public void componentShown(ComponentEvent e) {}

	@Override
	public void componentHidden(ComponentEvent e) {}
	
	@Override
	public void keyPressed(KeyEvent e) {
		switch (e.getKeyCode()) {
			case KeyEvent.VK_ENTER:
			case KeyEvent.VK_LEFT:
			case KeyEvent.VK_RIGHT:
			case KeyEvent.VK_UP:
			case KeyEvent.VK_DOWN:
			case KeyEvent.VK_PAGE_UP:
			case KeyEvent.VK_PAGE_DOWN:
			case KeyEvent.VK_HOME:
			case KeyEvent.VK_END:
			case KeyEvent.VK_KP_UP:
			case KeyEvent.VK_KP_DOWN:
			case KeyEvent.VK_KP_LEFT:
			case KeyEvent.VK_KP_RIGHT:
				e.consume();
			default:
				return;
		}
	}
	
	@Override
	public void keyReleased(KeyEvent e) {}
	
	@Override
	public void keyTyped(KeyEvent e) {}
}
