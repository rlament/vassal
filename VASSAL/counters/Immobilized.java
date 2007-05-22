/*
 * $Id$
 *
 * Copyright (c) 2000-2003 by Rodney Kinney
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASSAL.counters;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.InputEvent;
import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.command.Command;
import VASSAL.tools.SequenceEncoder;

public class Immobilized extends Decorator implements EditablePiece {

  public static final String ID = "immob;";
  protected boolean shiftToSelect = false;
  protected boolean ctlShiftToSelect = false;
  protected boolean ignoreGrid = false;
  protected boolean neverSelect = false;
  protected boolean neverMove = false;
  protected boolean moveIfSelected = false;
  protected EventFilter selectFilter;
  protected EventFilter moveFilter;

  protected static final char MOVE_SELECTED = 'I';
  protected static final char MOVE_NORMAL = 'N';
  protected static final char NEVER_MOVE = 'V';
  protected static final char IGNORE_GRID = 'g';
  protected static final char SHIFT_SELECT = 'i';
  protected static final char CTL_SHIFT_SELECT = 'c';
  protected static final char NEVER_SELECT = 'n';

  protected class UseShift implements EventFilter {
    public boolean rejectEvent(InputEvent evt) {
      return !evt.isShiftDown() && !Boolean.TRUE.equals(getProperty(Properties.SELECTED));
    }
  };

  protected class UseCtlShift implements EventFilter {
    public boolean rejectEvent(InputEvent evt) {
      return !(evt.isShiftDown() && evt.isControlDown()) &&!Boolean.TRUE.equals(getProperty(Properties.SELECTED));
    }
  };
  
  
  protected class MoveIfSelected implements EventFilter {
    public boolean rejectEvent(InputEvent evt) {
      return !Boolean.TRUE.equals(getProperty(Properties.SELECTED));
    }
  }

  protected static EventFilter NEVER = new EventFilter() {
    public boolean rejectEvent(InputEvent evt) {
      return true;
    }
  };

  public Immobilized() {
    this(null, Immobilized.ID);
  }

  public Immobilized(GamePiece p, String type) {
    setInner(p);
    mySetType(type);
  }

  public void mySetType(String type) {
    shiftToSelect = false;
    ctlShiftToSelect = false;
    neverSelect = false;
    ignoreGrid = false;
    neverMove = false;
    moveIfSelected = false;
    SequenceEncoder.Decoder st = new SequenceEncoder.Decoder(type, ';');
    st.nextToken();
    String selectionOptions = st.nextToken("");
    String movementOptions = st.nextToken("");
    if (selectionOptions.indexOf(SHIFT_SELECT) >= 0) {
      shiftToSelect = true;
      moveIfSelected = true;
    }
    if (selectionOptions.indexOf(CTL_SHIFT_SELECT) >= 0) {
      ctlShiftToSelect = true;
      moveIfSelected = true;
    }
    if (selectionOptions.indexOf(NEVER_SELECT) >= 0) {
      neverSelect = true;
      neverMove = true;
    }
    if (selectionOptions.indexOf(IGNORE_GRID) >= 0) {
      ignoreGrid = true;
    }
    if (movementOptions.length() > 0) {
      switch (movementOptions.charAt(0)) {
        case NEVER_MOVE:
          neverMove = true;
          moveIfSelected = false;
          break;
        case MOVE_SELECTED:
          neverMove = false;
          moveIfSelected = true;
          break;
        default :
          neverMove = false;
          moveIfSelected = false;
      }
    }
    if (neverSelect) {
      selectFilter = NEVER;
    }
    else if (shiftToSelect) {
      selectFilter = new UseShift();
    }
    else if (ctlShiftToSelect) {
      selectFilter = new UseCtlShift();
    }
    else {
      selectFilter = null;
    }
    if (neverMove) {
      moveFilter = NEVER;
    }
    else if (moveIfSelected) {
      moveFilter = new MoveIfSelected();
    }
    else {
      moveFilter = null;
    }
  }

  public String getName() {
    return piece.getName();
  }

  public KeyCommand[] myGetKeyCommands() {
    return new KeyCommand[0];
  }

  public Command myKeyEvent(KeyStroke e) {
    return null;
  }

  public Object getProperty(Object key) {
    if (Properties.NO_STACK.equals(key)) {
      return Boolean.TRUE;
    }
    else if (Properties.TERRAIN.equals(key)) {
      return new Boolean(moveIfSelected || neverMove);
    }
    else if (Properties.IGNORE_GRID.equals(key)) {
      return new Boolean(ignoreGrid);
    }
    else if (Properties.SELECT_EVENT_FILTER.equals(key)) {
      return selectFilter;
    }
    else if (Properties.MOVE_EVENT_FILTER.equals(key)) {
      return moveFilter;
    }
    else if (Properties.NON_MOVABLE.equals(key)) {
        return neverMove;
    }
    else {
      return super.getProperty(key);
    }
  }

  public void draw(Graphics g, int x, int y, Component obs, double zoom) {
    piece.draw(g, x, y, obs, zoom);
  }

  public Rectangle boundingBox() {
    return piece.boundingBox();
  }

  public Shape getShape() {
    return piece.getShape();
  }

  public String myGetType() {
    StringBuffer buffer = new StringBuffer(ID);
    if (neverSelect) {
      buffer.append(NEVER_SELECT);
    }
    else if (shiftToSelect) {
      buffer.append(SHIFT_SELECT);
    }
    else if (ctlShiftToSelect) {
      buffer.append(CTL_SHIFT_SELECT);
    }
    if (ignoreGrid) {
      buffer.append(IGNORE_GRID);
    }
    buffer.append(';');
    if (neverMove) {
      buffer.append(NEVER_MOVE);
    }
    else if (moveIfSelected) {
      buffer.append(MOVE_SELECTED);
    }
    else {
      buffer.append(MOVE_NORMAL);
    }
    return buffer.toString();
  }

  public String myGetState() {
    return "";
  }

  public void mySetState(String s) {
  }

  public String getDescription() {
    return "Does not stack";
  }

  public HelpFile getHelpFile() {
    return HelpFile.getReferenceManualPage("NonStacking.htm");
  }

  public PieceEditor getEditor() {
    return new Ed(this);
  }

  private static class Ed implements PieceEditor {
    private JComboBox selectionOption;
    private JComboBox movementOption;
    private JCheckBox ignoreGridBox;
    private Box controls;

    public Ed(Immobilized p) {
      selectionOption = new JComboBox();
      selectionOption.addItem("normally");
      selectionOption.addItem("when shift-key down");
      selectionOption.addItem("when ctl-shift-key down");
      selectionOption.addItem("never");
      if (p.neverSelect) {
        selectionOption.setSelectedIndex(3);
      }
      else if (p.ctlShiftToSelect) {
        selectionOption.setSelectedIndex(2);
      }
      else if (p.shiftToSelect) {
        selectionOption.setSelectedIndex(1);
      }
      else {
        selectionOption.setSelectedIndex(0);
      }
      ignoreGridBox = new JCheckBox("Ignore map grid when moving?");
      ignoreGridBox.setSelected(p.ignoreGrid);
      controls = Box.createVerticalBox();
      Box b = Box.createHorizontalBox();
      b.add(new JLabel("Select piece:  "));
      b.add(selectionOption);
      controls.add(b);

      movementOption = new JComboBox();
      movementOption.addItem("normally");
      movementOption.addItem("only if selected");
      movementOption.addItem("never");
      if (p.neverMove) {
        movementOption.setSelectedIndex(2);
      }
      else if (p.moveIfSelected) {
        movementOption.setSelectedIndex(1);
      }
      else {
        movementOption.setSelectedIndex(0);
      }
      b = Box.createHorizontalBox();
      b.add(new JLabel("Move piece:  "));
      b.add(movementOption);
      controls.add(b);
      controls.add(ignoreGridBox);
    }

    public String getState() {
      return "";
    }

    public String getType() {
      String s = ID;
      switch (selectionOption.getSelectedIndex()) {
        case 1:
          s += SHIFT_SELECT;
          break;
        case 2:
          s += CTL_SHIFT_SELECT;
          break;
        case 3:
          s += NEVER_SELECT;
      }
      if (ignoreGridBox.isSelected()) {
        s += IGNORE_GRID;
      }
      s += ';';
      switch (movementOption.getSelectedIndex()) {
        case 0:
          s += MOVE_NORMAL;
          break;
        case 1:
          s += MOVE_SELECTED;
          break;
        case 2:
          s += NEVER_MOVE;
          break;
      }
      return s;
    }

    public Component getControls() {
      return controls;
    }
  }
}

