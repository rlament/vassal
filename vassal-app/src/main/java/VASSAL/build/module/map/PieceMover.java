/*
 *
 * Copyright (c) 2000-2003 by Rodney Kinney, Jim Urbas
 * Refactoring of DragHandler Copyright 2011 Pieter Geerkens
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
package VASSAL.build.module.map;

import VASSAL.build.AbstractBuildable;
import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.module.Chatter;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.GlobalOptions;
import VASSAL.build.module.Map;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.widget.PieceSlot;
import VASSAL.command.ChangeTracker;
import VASSAL.command.Command;
import VASSAL.command.NullCommand;
import VASSAL.configure.NamedHotKeyConfigurer;
import VASSAL.counters.BasicPiece;
import VASSAL.counters.BoundsTracker;
import VASSAL.counters.Deck;
import VASSAL.counters.DeckVisitor;
import VASSAL.counters.DeckVisitorDispatcher;
import VASSAL.counters.Decorator;
import VASSAL.counters.DragBuffer;
import VASSAL.counters.EventFilter;
import VASSAL.counters.GamePiece;
import VASSAL.counters.Highlighter;
import VASSAL.counters.Immobilized;
import VASSAL.counters.KeyBuffer;
import VASSAL.counters.Mat;
import VASSAL.counters.MatCargo;
import VASSAL.counters.MatHolder;
import VASSAL.counters.PieceFinder;
import VASSAL.counters.PieceIterator;
import VASSAL.counters.PieceSorter;
import VASSAL.counters.PieceVisitorDispatcher;
import VASSAL.counters.Properties;
import VASSAL.counters.PropertyExporter;
import VASSAL.counters.Stack;
import VASSAL.tools.DebugControls;
import VASSAL.tools.FormattedString;
import VASSAL.tools.LaunchButton;
import VASSAL.tools.NamedKeyStroke;
import VASSAL.tools.image.ImageUtils;
import VASSAL.tools.imageop.Op;
import VASSAL.tools.swing.SwingUtils;

import org.apache.commons.lang3.SystemUtils;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import static VASSAL.build.module.Map.MAP_NAME;

/**
 * PieceMover handles the "Drag and Drop" of pieces and stacks, onto or within a Map window. It implements
 * MouseListener and handles dragging and dropping of both individual pieces, stacks, and groups of
 * pieces/stacks. It is a subcomponent of Map.
 * <br><br>
 * For the selection/deselection of pieces and band-selecting pieces by "dragging a lasso around them",
 * see {@link KeyBufferer}.
 */
public class PieceMover extends AbstractBuildable
                        implements MouseListener,
                                   MouseMotionListener,
                                   GameComponent,
                                   Comparator<GamePiece> {
  /** The Preferences key for auto-reporting moves. */
  public static final String AUTO_REPORT = "autoReport"; //$NON-NLS-1$
  public static final String NAME = "name"; //NON-NLS

  public static final String HOTKEY = "hotkey"; //NON-NLS

  protected Map map;                           // Map we're the PieceMover for.
  protected Point dragBegin;                   // Anchor point for drag and drop
  protected boolean breachedThreshold;         // True if we've breached the dragThreshold during this drag
  protected GamePiece dragging;                // Anchor piece that we're dragging (along with everything else in DragBuffer)

  // PieceMover provides the "clear move history" button for Map - the configurer dialog is found there.
  protected LaunchButton markUnmovedButton;
  protected String markUnmovedText;
  protected String markUnmovedIcon;
  protected NamedKeyStroke markUnmovedHotkey;
  protected String markUnmovedReport;
  public static final String ICON_NAME = "icon"; //$NON-NLS-1$
  protected String iconName;

  protected PieceFinder dragTargetSelector;            // Selects drag target from mouse click on the Map
  protected PieceFinder dropTargetSelector;            // Selects piece to merge with at the drop destination
  protected LoadedCargoDropTargetSelector loadedCargoDropTargetSelector;       // Specialised for cargo loaded on a mat
  protected MatDropTargetSelector matDropTargetSelector;                       // Specialised for mats
  protected PieceVisitorDispatcher selectionProcessor; // Processes drag target after having been selected
  protected Comparator<GamePiece> pieceSorter = new PieceSorter();

  protected int curPieceOffsetX;
  protected int curPieceOffsetY;

  public void setCurPieceOffset(int x, int y) {
    curPieceOffsetX = x;
    curPieceOffsetY = y;
  }

  public void setBreachedThreshold(boolean breachedThreshold) {
    this.breachedThreshold = breachedThreshold;
  }

  public boolean getBreachedThreshold() {
    return breachedThreshold;
  }

  /**
   * Adds this component to its parent map. Add ourselves as a mouse listener, drag gesture listener, etc.
   * @param b Map to add to
   */
  @Override
  public void addTo(Buildable b) {
    // Create our target selection filters
    dragTargetSelector = createDragTargetSelector();
    selectionProcessor = createSelectionProcessor();
    dropTargetSelector = createDropTargetSelector(); // Obsolete from 3.6, but maintains backwards-compatibility with e.g. VASL

    // Register with our parent map
    map = (Map) b;
    map.addLocalMouseListener(this);
    GameModule.getGameModule().getGameState().addGameComponent(this);

    DragHandler.addPieceMover(this);
    map.getView().addMouseMotionListener(this);
    map.setDragGestureListener(DragHandler.getTheDragHandler());
    map.setPieceMover(this);

    // Because of the strange legacy scheme of halfway-running a Toolbar button "on behalf of Map", we have to set some its attributes
    setAttribute(Map.MARK_UNMOVED_TEXT,
                 map.getAttributeValueString(Map.MARK_UNMOVED_TEXT));
    setAttribute(Map.MARK_UNMOVED_ICON,
                 map.getAttributeValueString(Map.MARK_UNMOVED_ICON));
    setAttribute(Map.MARK_UNMOVED_HOTKEY,
                 map.getAttributeValueString(Map.MARK_UNMOVED_HOTKEY));
    setAttribute(Map.MARK_UNMOVED_REPORT,
                 map.getAttributeValueString(Map.MARK_UNMOVED_REPORT));
  }

  /**
   * Creates a {@link MovementReporter} for a collection of commands containing AddPiece and MovePiece commands, which
   * will supply auto-report messages corresponding to those commands
   * @param c Command -- presumably including some AddPiece and MovePiece commands.
   * @return MovementReporter to auto-report the command(s)
   */
  protected MovementReporter createMovementReporter(Command c) {
    return new MovementReporter(c);
  }

  /**
   * Reports the image files we use (forremove-unwanted-image-files and/or search)
   * @param s Collection to add image names to
   */
  @Override
  public void addLocalImageNames(Collection<String> s) {
    if (iconName != null) s.add(iconName);
    if (markUnmovedIcon != null) s.add(markUnmovedIcon);
  }

  /**
   * Return a DropTargetSelector suitable for the type of piece being moved
   *
   * When the user *completes* a drag-and-drop operation, the pieces being dragged will either be:
   * <br>(1) combined in a stack with an existing piece (or stack, or deck) on the map
   * <br>(2) placed alone in a brand new stack
   * <br>(3) or, in the case of non-stacking pieces, placed on the map without stack.
   * <br><br>
   * For <i>each</i> of the stacks/pieces being dragged, we need to determine which of those outcomes
   * applies. This "drop target selector" will be fed, one by one, all of the pieces on the map, and for
   * each one must check whether it would make a valid "merge target" for the current dragged piece under
   * consideration (the PieceFinder's {@link PieceFinder#select} method provides the map and location, and
   * the PieceMover's {@link PieceMover#dragging} field tells the "dragged piece under consideration". Each
   * method is to returns the target piece/stack/deck to merge with if it has been passed a valid target, or
   * null if it is not a valid match.
   * <br><br>
   * The Map's {@link Map#findAnyPiece(Point, PieceFinder)} method, which feeds this, will be responsible for
   * iterating through the list of possible pieces in proper visual order so that we check the pieces "visually
   * on top" first.
   * <br><br>
   *
   * @param piece The Piece being mover
   * @param cargo The MatCargo trait of the piece being moved
   * @param mat The Mat the piece is loaded on, if it is cargo
   * @return a {@link PieceFinder} instance that determines which {@link GamePiece} (if any) to
   *   combine the being-dragged pieces with.
   */
  protected PieceFinder getDropTargetSelector(GamePiece piece, MatCargo cargo, Mat mat) {
    if (cargo != null && mat != null) {
      if (loadedCargoDropTargetSelector == null) {
        loadedCargoDropTargetSelector = new LoadedCargoDropTargetSelector();
      }
      loadedCargoDropTargetSelector.setPiece(piece, cargo, mat);
      return loadedCargoDropTargetSelector;
    }
    else if (mat != null) {
      if (matDropTargetSelector == null) {
        matDropTargetSelector = new MatDropTargetSelector();
      }
      matDropTargetSelector.setPiece(piece, cargo, mat);
      return matDropTargetSelector;
    }

    if (dropTargetSelector == null) {
      dropTargetSelector = new StandardDropTargetSelector();
    }
    ((StandardDropTargetSelector) dropTargetSelector).setPiece(piece, cargo, mat);
    return dropTargetSelector;
  }

  // Retain for compatibility
  protected PieceFinder createDropTargetSelector() {
    return new StandardDropTargetSelector();
  }

  /**
   * Default DropTargetSelector for standard pieces
   */
  private class StandardDropTargetSelector extends PieceFinder.Movable {
    // The piece being moved
    private GamePiece piece;
    // The MatCargo trait of the piece being moved
    private MatCargo cargo;
    // The Mat trait the moving piece is currently loaded on (if cargo)
    private Mat mat;

    public StandardDropTargetSelector() {
      super();
    }

    public void setPiece(GamePiece piece, MatCargo cargo, Mat mat) {
      this.piece = piece;
      this.cargo = cargo;
      this.mat = mat;
    }

    public GamePiece getPiece() {
      return piece;
    }

    public MatCargo getCargo() {
      return cargo;
    }

    public Mat getMat() {
      return mat;
    }

    /**
     * When a deck exists on the map, and we need to find out if our piece was dragged to the deck
     * @param d Potential target {@link Deck}
     * @return the Deck if our target location is inside the footprint of the Deck, or null if not
     */
    @Override
    public Object visitDeck(Deck d) {
      final Point pos = d.getPosition();
      final Point p = new Point(pt.x - pos.x, pt.y - pos.y);
      return d.getShape().contains(p) ? d : null;
    }

    /**
     * When an unstacked piece exists on the map, we see if this is a piece we could
     * form a stack with -- if it is at our precise location (or the location we would
     * be getting snapped to). We must also check if stacking is enabled and if both
     * the target "piece" and the "dragging" piece fit all the usual "can merge" requirements
     * (both are "stacking" pieces, and in the same visual layer)
     * @param piece Potential target piece on the map.
     * @return The piece to stack with, if we should, otherwise null.
     */
    @Override
    public Object visitDefault(GamePiece piece) {
      GamePiece selected = null;
      if (this.map.getStackMetrics().isStackingEnabled() &&
        this.map.getPieceCollection().canMerge(dragging, piece)) {
        if (this.map.isLocationRestricted(pt)) {
          final Point snap = this.map.snapTo(pt);
          if (piece.getPosition().equals(snap)) {
            selected = piece;
          }
        }
        else {
          // The "super" visitDefault checks if our point is inside the visual boundaries of the piece.
          selected = (GamePiece) super.visitDefault(piece);
        }
      }

      // We don't drag a piece "to itself".
      if (selected != null &&
        DragBuffer.getBuffer().contains(selected) &&
        selected.getParent() != null &&
        selected.getParent().topPiece() == selected) {  //NOTE: topPiece() returns the top VISIBLE piece (not hidden by Invisible trait)
        selected = null;
      }

      // Don't combine a non-cargo piece with cargo loaded on a mat
      if (selected != null && getCargo() == null) {
        final MatCargo targetCargo = (MatCargo) Decorator.getDecorator(selected, MatCargo.class);
        if (targetCargo != null && targetCargo.getMat() != null) {
          selected = null;
        }
      }

      return selected;
    }

    /**
     * When a stack already exists on the map, we see if this piece could be added to it.
     * @param s Stack to check if we're appropriately configured to merge with
     * @return The piece to stack with, if we should, otherwise null.
     */
    @Override
    public Object visitStack(Stack s) {
      GamePiece selected = null;
      if (this.map.getStackMetrics().isStackingEnabled() &&
        this.map.getPieceCollection().canMerge(dragging, s) &&
        !DragBuffer.getBuffer().contains(s) &&
        !DragBuffer.getBuffer().containsAllMembers(s) &&  //BR// Don't merge back into a stack we are in the act of emptying
        s.topPiece() != null) {  //NOTE: topPiece() returns the top VISIBLE piece (not hidden by Invisible trait)

        // If the target point is on a snappable grid and the target stack is not expanded, then snap the point
        // and see if it aligns with the Stack location
        // Exceptions: 1. Cargo loaded on a Mat can stack off-grid
        //             2. 'Ignore Grid' pieces can now stack normally, so can create valid off-grid stacks
        final boolean stackIgnoresGrid = isStackLoadedCargo(s) || Boolean.TRUE.equals(s.getPieceAt(0).getProperty(Properties.IGNORE_GRID));

        if (this.map.isLocationRestricted(pt) && !s.isExpanded() && !stackIgnoresGrid) {
          if (s.getPosition().equals(this.map.snapTo(pt))) {
            selected = s;
          }
        }
        else {
          // The super's visitStack handles checking to see if our point is inside the visual "shape" of the stack
          selected = (GamePiece) super.visitStack(s);
        }
      }

      // Don't combine a non-cargo piece with a stack of cargo loaded on a mat
      if (getCargo() == null && isStackLoadedCargo(selected)) {
        selected = null;
      }

      return selected;
    }

    protected boolean isStackLoadedCargo(GamePiece p) {
      MatCargo targetCargo = null;
      if (p instanceof Stack) {
        final Stack s = (Stack)p;
        if (s.getPieceCount() > 0) {
          targetCargo = (MatCargo) Decorator.getDecorator(s.getPieceAt(0), MatCargo.class);
        }
      }
      else if (p instanceof Decorator) {
        targetCargo = (MatCargo) Decorator.getDecorator(p, MatCargo.class);
      }
      return targetCargo != null && targetCargo.getMat() != null;
    }
  }

  /**
   * A DropTargetSeletor for Cargo that is loaded onto a Mat.
   * We are only interested in stacking with other Counters also
   * loaded on the same mat.
   */
  private class LoadedCargoDropTargetSelector extends StandardDropTargetSelector {
    /**
     * Loaded Cargo will never enter a Deck
     * @param d Potential target {@link Deck}
     */
    @Override
    public Object visitDeck(Deck d) {
      return null;
    }

    /**
     * Only interested in stacking with Cargo loaded on the same Mat
     * @param piece Potential target piece on the map.
     */
    @Override
    public Object visitDefault(GamePiece piece) {
      return getMat().hasCargo(piece) ? super.visitDefault(piece) : null;
    }

    /**
     * Only interested in stacking with another Stack if it has at least one piece
     * and the pieces are on this mat
     * @param s Potential target piece on the map.
     */
    @Override
    public Object visitStack(Stack s) {
      if (s != null && s.getPieceCount() > 0) {
        return getMat().hasCargo(s.getPieceAt(0)) ? super.visitStack(s) : null;
      }
      return null;
    }
  }

  /**
   * DropTargetSelector for moving mats. Mats holding cargo must never
   * enter a Deck
   */
  private class MatDropTargetSelector extends StandardDropTargetSelector {
    /**
     * Mats with loaded Cargo must never enter a Deck
     * @param d Potential target {@link Deck}
     */
    @Override
    public Object visitDeck(Deck d) {
      return getMat().getCargoCount() > 0 ? null : super.visitDeck(d);
    }
  }

  /**
   * When the user *starts* a potential drag-and-drop operation by clicking
   * on the map, a piece from the map is selected by the dragTargetSelector.
   * What happens to that piece is determined by the {@link PieceVisitorDispatcher}
   * instance returned by this method. The default implementation does the following:
   * <br>(1) If a Deck, add the (single) top piece to the drag buffer
   * <br>(2) If a stack, add it to the drag buffer. If the stack is a member of the "currently selected pieces"
   * (i.e. {@link KeyBuffer}), then add any other already-selected pieces and stacks to the drag buffer as well.
   * <br>(3) Otherwise, add the piece and any other multi-selected pieces and stacks to the drag buffer.
   *
   * @see #createDragTargetSelector
   * @return Dispatcher
   */
  protected PieceVisitorDispatcher createSelectionProcessor() {
    return new DeckVisitorDispatcher(new DeckVisitor() {
      /**
       * We've picked a Deck - Clear the drag buffer and add Deck's top piece to the drag buffer.
       * @param d Deck we clicked on
       * @return null
       */
      @Override
      public Object visitDeck(Deck d) {
        // Don't allow drag from deck if we don't have access to it
        if (!d.isAccessible()) {
          return null;
        }

        final DragBuffer dbuf = DragBuffer.getBuffer();
        dbuf.clear();
        for (final PieceIterator it = d.drawCards(); it.hasMoreElements();) {
          final GamePiece p = it.nextPiece();
          p.setProperty(Properties.OBSCURED_BY, p.getProperty(Properties.OBSCURED_BY_PRE_DRAW)); // Bug 13433 restore correct OBSCURED_BY
          dbuf.add(p);
        }
        return null;
      }

      /**
       * We've picked a Stack. Clear the drag buffer and add the stack's pieces to it. If the stack is part of the
       * "currently selected pieces" (i.e. {@link KeyBuffer}), then add any other pieces/stacks as well.
       * @param s Stack we clicked on
       * @return null
       */
      @Override
      public Object visitStack(Stack s) {
        final DragBuffer dbuf = DragBuffer.getBuffer();
        dbuf.clear();
        // RFE 1629255 - Only add selected pieces within the stack to the DragBuffer,
        // except when global pref "MOVING_STACKS_PICKUP_UNITS" is set
        final boolean selectAllUnitsInStackRegardlessOfSelection =
            (Boolean) GameModule.getGameModule().getPrefs().getValue(Map.MOVING_STACKS_PICKUP_UNITS);

        s.asList().forEach(
          selectAllUnitsInStackRegardlessOfSelection ?
            dbuf::add :
          (p) -> {
            if (Boolean.TRUE.equals(p.getProperty(Properties.SELECTED))) {
              dbuf.add(p);
            }
          }
        );
        // End RFE 1629255

        final KeyBuffer kbuf = KeyBuffer.getBuffer();
        if (kbuf.containsChild(s)) {
          // If clicking on a stack with a selected piece, put all selected
          // pieces in other stacks into the drag buffer
          kbuf.sort(PieceMover.this);
          for (final GamePiece piece : kbuf.asList()) {
            if (piece.getParent() != s) {
              dbuf.add(piece);
            }
          }
        }
        return null;
      }

      /**
       * We've clicked a regular (non-stacked) piece. Clear drag buffer and the piece.
       * @param selected piece clicked on
       * @return null
       */
      @Override
      public Object visitDefault(GamePiece selected) {
        final DragBuffer dbuf = DragBuffer.getBuffer();
        dbuf.clear();

        final KeyBuffer kbuf = KeyBuffer.getBuffer();
        if (kbuf.contains(selected)) {
          // If clicking on a selected piece, put all selected pieces into the
          // drag buffer
          kbuf.sort(PieceMover.this);
          for (final GamePiece piece : kbuf.asList()) {
            dbuf.add(piece);
          }
        }
        else {
          dbuf.add(selected);
        }

        // Mat Support: if we're dragging a Mat, bring its cargo too
        if (GameModule.getGameModule().isMatSupport()) {
          final List<GamePiece> checkDrag = new ArrayList<>(dbuf.asList());
          for (final GamePiece piece : checkDrag) {
            final List<GamePiece> cargoList = (List<GamePiece>) piece.getProperty(Mat.MAT_CONTENTS);
            if (cargoList != null) {
              for (final GamePiece cargo : cargoList) {
                if (!dbuf.contains(cargo)) {
                  dbuf.add(cargo);
                }
              }
            }
          }
        }
        return null;
      }
    });
  }

  /**
   * Returns the {@link PieceFinder} instance that will select a
   * {@link GamePiece} for processing when the user clicks on the map.
   * The default implementation is to return the first piece whose shape
   * contains the point clicked on.
   *
   * @return Piece Finder
   */
  protected PieceFinder createDragTargetSelector() {
    return new PieceFinder.Movable() {
      @Override
      public Object visitDeck(Deck d) {
        if (!d.isAccessible()) {
          return null;
        }
        final Point pos = d.getPosition();
        final Point p = new Point(pt.x - pos.x, pt.y - pos.y);
        return d.boundingBox().contains(p) && d.getPieceCount() > 0 ? d : null;
      }
    };
  }

  /**
   * Detects when a game is starting, for purposes of managing the mark-unmoved button.
   * @param gameStarting if true, a game is starting.  If false, then a game is ending
   */
  @Override
  public void setup(boolean gameStarting) {
    if (gameStarting) {
      initButton();
    }
  }

  /**
   * PieceMover has nothing to save/restore in a save file.
   * @return null
   */
  @Override
  public Command getRestoreCommand() {
    return null;
  }

  /**
   * @param name Name of icon file
   * @return Image for button icon
   */
  private Image loadIcon(String name) {
    if (name == null || name.length() == 0) return null;
    return Op.load(name).getImage();
  }

  /**
   * PieceMover manages the "Mark All Pieces Unmoved" button for the map.
   */
  protected void initButton() {
    final String value = getMarkOption();
    if (!GlobalOptions.NEVER.equals(value)) {
      if (markUnmovedButton == null) {
        final ActionListener al = e -> {
          final GamePiece[] p = map.getAllPieces();
          final Command c = new NullCommand();
          for (final GamePiece gamePiece : p) {
            c.append(markMoved(gamePiece, false, false));
          }

          if ((markUnmovedReport != null) && !markUnmovedReport.isEmpty()) {
            final FormattedString format = new FormattedString(markUnmovedReport);
            format.setProperty(MAP_NAME, map.getConfigureName());
            final String reportText = format.getLocalizedText(map, this, "Editor.Map.mark_unmoved_button_report");

            if (!reportText.isEmpty()) {
              final Command display = new Chatter.DisplayText(GameModule.getGameModule().getChatter(), "* " + reportText);
              display.execute();
              c.append(display);
            }
          }

          GameModule.getGameModule().sendAndLog(c);
          map.repaint();
        };

        markUnmovedButton =
          new LaunchButton("", NAME, Map.MARK_UNMOVED_HOTKEY, Map.MARK_UNMOVED_ICON, al);

        Image img = null;
        if (iconName != null && iconName.length() > 0) {
          img = loadIcon(iconName);
          if (img != null) {
            markUnmovedButton.setAttribute(Map.MARK_UNMOVED_ICON, iconName);
          }
        }

        if (img == null) {
          img = loadIcon(markUnmovedIcon);
          if (img != null) {
            markUnmovedButton.setAttribute(Map.MARK_UNMOVED_ICON, markUnmovedIcon);
          }
        }

        markUnmovedButton.setAttribute(Map.MARK_UNMOVED_HOTKEY, map.getAttributeValueString(Map.MARK_UNMOVED_HOTKEY));

        markUnmovedButton.setAlignmentY(0.0F);
        markUnmovedButton.setText(markUnmovedText);
        markUnmovedButton.setToolTipText(
          map.getAttributeValueString(Map.MARK_UNMOVED_TOOLTIP));
        map.getToolBar().add(markUnmovedButton);
      }
    }
    else if (markUnmovedButton != null) {
      map.getToolBar().remove(markUnmovedButton);
      markUnmovedButton = null;
    }
  }

  /**
   * @return Our setting w/ regard to marking pieces moved.
   */
  private String getMarkOption() {
    final String value = map.getAttributeValueString(Map.MARK_MOVED);
    return (value == null) ? GlobalOptions.ALWAYS : value;
  }

  @Override
  public String[] getAttributeNames() {
    return new String[]{ICON_NAME};
  }

  @Override
  public String getAttributeValueString(String key) {
    return ICON_NAME.equals(key) ? iconName : null;
  }

  @Override
  public void setAttribute(String key, Object value) {
    if (ICON_NAME.equals(key)) {
      iconName = (String) value;
    }
    else if (Map.MARK_UNMOVED_TEXT.equals(key)) {
      if (markUnmovedButton != null) {
        markUnmovedButton.setAttribute(NAME, value);
      }
      markUnmovedText = (String) value;
    }
    else if (Map.MARK_UNMOVED_ICON.equals(key)) {
      if (markUnmovedButton != null) {
        markUnmovedButton.setAttribute(Map.MARK_UNMOVED_ICON, value);
      }
      markUnmovedIcon = (String) value;
    }
    else if (Map.MARK_UNMOVED_HOTKEY.equals(key)) {
      if (markUnmovedButton != null) {
        markUnmovedButton.setAttribute(Map.MARK_UNMOVED_HOTKEY, value);
      }
      if (value instanceof String) {
        value = NamedHotKeyConfigurer.decode((String) value);
      }
      markUnmovedHotkey = (NamedKeyStroke) value;
    }
    else if (Map.MARK_UNMOVED_REPORT.equals(key)) {
      markUnmovedReport = (String) value;
    }
  }

  protected boolean isMultipleSelectionEvent(MouseEvent e) {
    return e.isShiftDown();
  }

  /**
   * Invoked just BEFORE a piece is moved. Sets the "OldLocations" properties for the piece. Marks the piece
   * as "moved" if it has changed positions, and removes the piece from its old stack, if any.
   * @return Command encapsulating anything this method did, for replay in log file or on other clients
   */
  protected Command movedPiece(GamePiece p, Point loc) {
    // Make sure we don't end up sending I'm-empty commands from decks that were emptied by other players (e.g. online or log-step)
    GameModule.getGameModule().getDeckManager().clearEmptyDecksList();

    Command c = new NullCommand();
    c = c.append(setOldLocations(p));
    if (!loc.equals(p.getPosition())) {
      c = c.append(markMoved(p, true, false));
    }
    if (p.getParent() != null) {
      final Command removedCommand = p.getParent().pieceRemoved(p);
      c = c.append(removedCommand);
    }
    return c;
  }

  /**
   * Populates the "OldLocations" properties (e.g. OldMap, OldZone, etc) for the piece (or for a stack, for all
   * the pieces contained in it), based on their present locations, in preparation for moving them to a new location.
   * @param p Piece (could be a stack)
   * @return Command encapsulating any changes made, for replay in log file or on other clients
   */
  protected Command setOldLocations(GamePiece p) {
    Command comm = new NullCommand();
    if (p instanceof Stack) {
      for (final GamePiece gamePiece : ((Stack) p).asList()) {
        comm = comm.append(Decorator.putOldProperties(gamePiece));
      }
    }
    else {
      comm = comm.append(Decorator.putOldProperties(p));
    }
    return comm;
  }

  public Command markMoved(GamePiece p, boolean hasMoved) {
    return markMoved(p, hasMoved, true);
  }

  /**
   * Handles marking pieces as "moved" or "not moved", based on Global Options settings. Updates the
   * "moved" property of the pieces, if they have one.
   * @param p Piece (could be a Stack)
   * @param hasMoved True if piece has just moved, false if it is to be reset to not-moved status
   * @param locDefinitelyChanged true if piece definitely changed locations or mats (for possibly ignoring small moves)
   * @return Command encapsulating any changes made, for replay in log file or on other clients
   */
  public Command markMoved(GamePiece p, boolean hasMoved, boolean locDefinitelyChanged) {
    if (GlobalOptions.NEVER.equals(getMarkOption())) {
      hasMoved = false;
    }

    Command c = new NullCommand();
    if (!hasMoved || shouldMarkMoved()) {
      if (p instanceof Stack) {
        for (final GamePiece gamePiece : ((Stack) p).asList()) {
          c = c.append(markMoved(gamePiece, hasMoved, locDefinitelyChanged));
        }
      }
      else if (p.getProperty(Properties.MOVED) != null) {
        if (p.getId() != null) {
          final ChangeTracker comm = new ChangeTracker(p);
          p.setProperty((!hasMoved || locDefinitelyChanged) ? Properties.MOVED : Properties.MAYBE_MOVED,
                        hasMoved ? Boolean.TRUE : Boolean.FALSE);
          c = c.append(comm.getChangeCommand());
        }
      }
    }
    return c;
  }

  /**
   * Checks Global Options settings (and if necessary, the user preference) about whether we mark moved
   * pieces as "moved" or not.
   * @return true if we should mark a moved piece as "moved", false if not.
   */
  protected boolean shouldMarkMoved() {
    final String option = getMarkOption();
    return !GlobalOptions.NEVER.equals(option);
  }

  /**
   * This is the key method for handling the "Drop" part of Drag and Drop.
   * <br>(1) Moves each piece in the {@link DragBuffer} to its proper destination,
   * based on the mouse having been released at point "p" (if multiple pieces
   * were being dragged after being e.g. band-selected, each individual piece's
   * destination point will vary with the piece's offset from the anhor point
   * that started the drag). This also involves removing each piece from any
   * stacks/decks it was part of, and possibly removing it from its old map if
   * it is changing maps.
   * <br>(2) As each piece is moved, finds appropriate "merge targets" (pieces
   * that should be combined with it in a stack) if they exist, or forms new stacks
   * where needed. Adds the piece to old or new stacks as appropriate, or directly
   * to the map if non-stacking. If the piece has been moved between maps (or onto
   * a map for the first time), the piece is also added to the map's piece collection.
   * <br>(3) If auto-reporting of moves is enabled, creates the report.
   * <br>(4) Applies any apply-on-move keystroke (from the "Key command to apply
   * to all units ending movement on this map" field of the map) to each piece, as
   * appropriate.
   * <br>(5) Returns a command to encapsulate any and all changes made, for replay
   * in log file or on other clients
   *
   * @param map Map
   * @param p Point mouse released
   * @return Command encapsulating all changes, for replay in log file or on other clients.
   */
  public Command movePieces(Map map, Point p) {
    // The DragBuffer contains the list of all pieces being dragged
    final PieceIterator it = DragBuffer.getBuffer().getIterator();
    if (!it.hasMoreElements()) return null;

    // This will be a list of every piece we end up moving somewhere, and thus the list of pieces to receive "apply-on-move" key command
    final List<GamePiece> allDraggedPieces = new ArrayList<>();

    Point offset = null;
    Command comm = new NullCommand();

    // This will track the area of the screen we need to repaint
    final BoundsTracker tracker = new BoundsTracker();

    // Map of Point->List<GamePiece> of pieces/stacks to merge with at a given
    // location. There is potentially a piece (stack) for each Game Piece Layer,
    // since stacks are only formed from pieces at the same visual Layer. See
    // LayeredPieceCollection for further details on visual layers.
    final java.util.Map<Point, List<GamePiece>> mergeTargets = new HashMap<>();

    // Split the pieces in the DragBuffer into types
    final List<GamePiece> otherPieces = new ArrayList<>();
    final List<GamePiece> cargoPieces = new ArrayList<>();
    final List<MatMover> matPieces = new ArrayList<>();

    while (it.hasMoreElements()) {
      final GamePiece piece = it.nextPiece();
      // Record the offset from p of the first piece in the DragBuffer
      if (offset == null) {
        offset = new Point(p.x - piece.getPosition().x, p.y - piece.getPosition().y);
      }
      if (Boolean.TRUE.equals(piece.getProperty(MatCargo.IS_CARGO))) {
        // Cargo
        cargoPieces.add(piece);
      }
      else if (piece.getProperty(Mat.MAT_ID) != null) {
        // Mat
        matPieces.add(new MatMover(piece));
      }
      else {
        // Other Pieces
        otherPieces.add(piece);
      }
    }

    // Remove any cargo currently on a mat from the general cargo list and add to its MatMover
    for (final MatMover mm : matPieces) {
      mm.grabCargo(cargoPieces);
    }

    // Build a new drag list that moves anything not loaded on a mat first, then each mat.
    // The cargo for each mat will be moved after the mat is moved, before the next mat
    final List<GamePiece> newDragBuffer = new ArrayList<>();
    newDragBuffer.addAll(otherPieces);
    newDragBuffer.addAll(cargoPieces);
    for (final MatMover mm : matPieces) {
      newDragBuffer.add(mm.getMatPiece());
      newDragBuffer.addAll(mm.getCargo());
    }

    final GameModule gm = GameModule.getGameModule();
    final boolean isMatSupport = gm.isMatSupport();

    // Non-null when a Mat being processed, or when cargo loaded on the Mat is being processed
    Mat currentMat = null;

    // Non-null when a MatCargo is being processed
    MatCargo currentCargo = null;

    for (final GamePiece gp : newDragBuffer) {

      dragging = gp;
      tracker.addPiece(dragging);

      // Keep track of the last mat moved, it will be the mat for the current Cargo.
      final Mat tempMat = (Mat) Decorator.getDecorator(gp, Mat.class);
      if (tempMat != null) {
        currentMat = tempMat;
      }
      currentCargo = (MatCargo) Decorator.getDecorator(gp, MatCargo.class);

      /*
       * Since the next "piece" might be a stack, make a list of
       * any pieces to be dragged in this loop. If we're dragging
       * a stack, the "stack" item itself will end up being emptied
       * and cleared in the coming merge process.
       */
      final List<GamePiece> draggedPieces = new ArrayList<>(0);
      if (dragging instanceof Stack) {
        draggedPieces.addAll(((Stack) dragging).asList());
      }
      else {
        draggedPieces.add(dragging);
      }

      if (offset != null) {
        p = new Point(dragging.getPosition().x + offset.x,
                      dragging.getPosition().y + offset.y);
      }

      // Retrieve any list of merge candidates we've already cached for the destination point
      List<GamePiece> mergeCandidates = mergeTargets.get(p);

      // Assume, for the moment, that we don't have anything to form a stack with.
      GamePiece mergeWith = null;

      // Find a piece (already moved in the same drag operation) that we can merge with at the destination point
      if (mergeCandidates != null) {
        final int n = mergeCandidates.size();
        for (int i = 0; i < n; ++i) {
          final GamePiece candidate = mergeCandidates.get(i);
          if (map.getPieceCollection().canMerge(candidate, dragging)) {
            mergeWith = candidate; // We have found an eligible piece to form a stack with!

            //FIXME I can't find a code path where this statement ever has any effect -- BR
            //FIXME Conceivably mergeCandidates is supposed to get "put" back to mergeTargets?
            //FIXME But since we're about to successfully merge with the piece already in there,
            //FIXME I'm not sure how adding "dragging" would change future mergability at all.
            mergeCandidates.set(i, dragging);

            break;
          }
        }
      }

      // If we're not forming a stack with one of our pieces that just moved, we now look for an already-existing
      // piece at the destination point
      if (mergeWith == null) {
        // This will now process through every "piece" (including stacks & decks) on the map, feeding each to the
        // dropTargetSelector (see our createDropTargetSelector method above). The dropTargetSelector will return
        // non-null if it finds a piece (including stack or deck) at this piece's drop location that is suitable to
        // merge with/into.
        mergeWith = map.findAnyPiece(p, getDropTargetSelector(dragging, currentCargo, currentMat));

        // If we get here with no merge target, we know we'll either be starting a new stack or putting the
        // piece by itself, so check if we need to do a "snap-to" of the grid.
        if (mergeWith == null) {
          boolean ignoreGrid = false;
          if (currentCargo == null) {
            // Non-cargo will just use the standard snapping rules
            final Boolean b = (Boolean) dragging.getProperty(Properties.IGNORE_GRID);
            ignoreGrid = b != null && b.booleanValue();
          }
          else {
            // Snapping behaviour of cargo depends on whether or not there is a valid mat at the destination
            // and whether or not we are moving with a Mat
            if (currentMat == null && currentCargo.locateNewMat(map, p) == null) {
              // Not moving with a Mat and there is no valid mat at the destination. If this cargo is coming from a mat,
              // then its real IGNORE_GRID is being masked by the MatCargo trait.
              final Boolean b = (Boolean) dragging.getProperty(Immobilized.BASE_IGNORE_GRID);
              ignoreGrid = b != null && b.booleanValue();
            }
            else {
              // Always ignore grid when moving to a Mat, or moving along with a mat
              ignoreGrid = true;
            }
          }

          // Snap if required
          if (!ignoreGrid) {
            p = map.snapTo(p);

            // Now that we've snapped to the grid, perhaps there is a piece here that we can merge with.
            mergeWith = map.findAnyPiece(p, getDropTargetSelector(dragging, currentCargo, currentMat));
          }
        }

        offset = new Point(p.x - dragging.getPosition().x,
                           p.y - dragging.getPosition().y);

        // If we've HAVE found a piece to merge with, and we're going to a map that allows stacking, then
        // add our current piece & the mergable one to our cached list of merge targets.
        if (mergeWith != null && map.getStackMetrics().isStackingEnabled()) {
          mergeCandidates = new ArrayList<>();
          mergeCandidates.add(dragging);
          mergeCandidates.add(mergeWith);
          mergeTargets.put(p, mergeCandidates);
        }
      }

      if (mergeWith == null) {
        // Now, if we never found a good merge target, we simply put the piece in the new position.
        comm = comm.append(movedPiece(dragging, p));  // Sets "old" locations, marks piece moved
        comm = comm.append(map.placeAt(dragging, p)); // Adds piece to destination map (removes from old map/stack)

        // If it was a stackable piece, we also need to start a new stack for it.
        if (!(dragging instanceof Stack) &&
            !Boolean.TRUE.equals(dragging.getProperty(Properties.NO_STACK))) {
          final Stack parent = map.getStackMetrics().createStack(dragging);
          if (parent != null) {
            comm = comm.append(map.placeAt(parent, p));

            //BR// We've made a new stack, so put it on the list of merge targets, in case more pieces land here too
            mergeCandidates = new ArrayList<>();
            mergeCandidates.add(dragging);
            mergeCandidates.add(parent);
            mergeTargets.put(p, mergeCandidates);
          }
        }
      }
      else {
        // If we get here, we DID find a piece (possibly Deck or Stack) to merge with/into, so we handle that.

        // Do not add pieces to a Deck that are Obscured to us, or that the Deck does not want to contain.
        // Removing them from the draggedPieces list will cause them to be left behind where the drag started.
        // NB. Pieces that have been dragged from a face-down Deck will be be Obscured to us, but will be Obscured
        // by the dummy user Deck.NO_USER
        if (mergeWith instanceof Deck) {
          final List<GamePiece> newList = new ArrayList<>(0);
          for (final GamePiece piece : draggedPieces) {
            if (((Deck) mergeWith).mayContain(piece) && ((Deck) mergeWith).isAccessible()) {
              final boolean isObscuredToMe = Boolean.TRUE.equals(piece.getProperty(Properties.OBSCURED_TO_ME));
              if (!isObscuredToMe || Deck.NO_USER.equals(piece.getProperty(Properties.OBSCURED_BY))) {
                newList.add(piece);
              }
            }
          }

          // If we rejected any dragged pieces for merging with the deck, update our list of dragged pieces for this iteration of the loop
          if (newList.size() != draggedPieces.size()) {
            draggedPieces.clear();
            draggedPieces.addAll(newList);
          }
        }

        // Add the remaining dragged counters to the target. If mergeWith is a single piece (not a Stack or Deck), then
        // we are merging into an expanded Stack and the merge order must be reversed to maintain the order of the merging
        // pieces.
        if (mergeWith instanceof Stack) { // Note - this could also be a Deck.
          for (final GamePiece draggedPiece : draggedPieces) {
            comm = comm.append(movedPiece(draggedPiece, mergeWith.getPosition()));
            comm = comm.append(map.getStackMetrics().merge(mergeWith, draggedPiece));
          }
        }
        else {
          for (int i = draggedPieces.size() - 1; i >= 0; --i) {
            comm = comm.append(movedPiece(draggedPieces.get(i), mergeWith.getPosition()));
            comm = comm.append(map.getStackMetrics().merge(mergeWith, draggedPieces.get(i)));
          }
        }
      }

      // Any piece we successfully moved, make sure is now considered a "selected piece" (i.e. a member of KeyBuffer)
      for (final GamePiece piece : draggedPieces) {
        KeyBuffer.getBuffer().add(piece);

        // Support for Mats and Cargo
        if (isMatSupport) {
          // If this is a piece that can be placed on mats, look for an overlapping mat, and put it there.
          if (Boolean.TRUE.equals(piece.getProperty(MatCargo.IS_CARGO))) {
            final MatCargo cargo = (MatCargo)Decorator.getDecorator(piece, MatCargo.class);
            final GamePiece oldMat = cargo.getMat();

            if ((oldMat == null) || (!draggedPieces.contains(oldMat) && !allDraggedPieces.contains(oldMat) && !DragBuffer.getBuffer().contains(oldMat))) {
              comm = comm.append(cargo.findNewMat(map, p));
            }
          }
          else if (piece.getProperty(Mat.MAT_NAME) != null) {
            // If the piece being moved is a Mat, check if any of our cargo is being "left behind"
            final Mat thisMat = (Mat)Decorator.getDecorator(piece, Mat.class);
            if (thisMat != null) {
              final List<GamePiece> contents = thisMat.getContents();
              for (final GamePiece cargo : contents) {
                // If the cargo is being dragged with us, it isn't being left behind
                if (draggedPieces.contains(cargo) || allDraggedPieces.contains(cargo) || DragBuffer.getBuffer().contains(cargo)) {
                  continue;
                }

                final MatCargo theCargo = (MatCargo)Decorator.getDecorator(cargo, MatCargo.class);
                if (theCargo != null) {
                  comm = comm.append(theCargo.findNewMat(cargo.getMap(), cargo.getPosition()));
                }
              }
            }
          }
        }
      }

      // Record each individual piece moved this iteration into our master list for this move
      allDraggedPieces.addAll(draggedPieces);

      // Add our piece's bounds to the bounds tracker
      tracker.addPiece(dragging);
    }

    // If there might be ignore-small-moves traits in pieces, track which locations actually changed
    if (GameModule.getGameModule().isTrueMovedSupport()) {
      comm = comm.append(doTrueMovedSupport(allDraggedPieces));
    }

    // We've finished the actual drag and drop of pieces, so we now create any auto-report message that is appropriate.
    if (GlobalOptions.getInstance().autoReportEnabled()) {
      final Command report = createMovementReporter(comm).getReportCommand().append(new MovementReporter.HiddenMovementReporter(comm).getReportCommand());
      report.execute();
      comm = comm.append(report);
    }

    // Applies any apply-on-move keystroke (from the "Key command to apply to all units ending movement on this map" field of the Map) to each piece
    if (map.getMoveKey() != null) {
      comm = comm.append(applyKeyAfterMove(allDraggedPieces, map.getMoveKey()));
    }

    // If we emptied any decks, let them send their I-am-empty key commands
    comm = gm.getDeckManager().checkEmptyDecks(comm);

    // Briefly suppress processing of action buttons on just-moved pieces
    KeyBuffer.getBuffer().setSuppressActionButtons(true);

    // Repaint any areas of the map window changed by our move
    tracker.repaint();

    return comm; // A command that, if executed, will fully replay the effects of this drag-and-drop on another client, or via a logfile.
  }

  /**
   * For a piece, mark it moved IF it changed locations or mats
   * @param p piece or stack
   * @return command to replicate any actions taken on another VASSAL instance
   */
  public Command checkTrueMoved(GamePiece p) {
    Command c = new NullCommand();
    if (p instanceof Stack) {
      for (final GamePiece gamePiece : ((Stack) p).asList()) {
        c = c.append(checkTrueMoved(gamePiece));
      }
    }
    else if (p.getProperty(Properties.MOVED) != null) {
      if (p.getId() != null) {
        c = c.append(p.checkTrueMoved());
      }
    }
    return c;
  }

  /**
   * For a list of pieces, mark them moved IF they changed locations or mats
   * @param pieces list of pieces
   * @return command to replicate any actions taken on another Vassal instance
   */
  protected Command doTrueMovedSupport(List<GamePiece> pieces) {
    if (GlobalOptions.NEVER.equals(getMarkOption()) || !shouldMarkMoved()) {
      return null;
    }

    Command comm = new NullCommand();
    for (final GamePiece piece : pieces) {
      comm = comm.append(checkTrueMoved(piece));
    }
    return comm;
  }


  /**
   * Applies a key command to each of a list of pieces.
   * @param pieces List of pieces
   * @param key keystroke to apply
   * @return Command that encapsulates the effects of the key command applied.
   */
  protected Command applyKeyAfterMove(List<GamePiece> pieces, KeyStroke key) {

    final GameModule gm = GameModule.getGameModule();
    // Multi-piece processing starting
    gm.initializeUiPieceProcessing(pieces.size());

    Command comm = new NullCommand();
    for (final GamePiece piece : pieces) {

      // Record next piece under way.
      gm.processNextUiPiece();

      if (piece.getProperty(Properties.SNAPSHOT) == null) {
        piece.setProperty(Properties.SNAPSHOT, ((PropertyExporter) piece).getProperties());
      }
      comm = comm.append(piece.keyEvent(key));
    }

    // Multi-piece processing finished
    gm.finalizeUiPieceProcessing();

    return comm;
  }

  /**
   * When mouse first pressed, set up for doing a possible drag-and-drop with them.
   *
   * On most systems, most of the drag-and-drop will be performed by the Drag Gesture Recognizer,
   * but this part runs for everyone, including setting an "anchor point" for the drag.
   *
   * @param e Event
   */
  @Override
  public void mousePressed(MouseEvent e) {
    if (canHandleEvent(e)) {
      selectMovablePieces(e);
    }
  }

  /**
   * When starting a drag-and-drop, place the clicked-on piece into the {@link DragBuffer}
   */
  protected void selectMovablePieces(MouseEvent e) {
    final GamePiece p = map.findPiece(e.getPoint(), dragTargetSelector);
    dragBegin = e.getPoint();
    breachedThreshold = false;
    setCurPieceOffset(0, 0);
    if (p != null) {
      final EventFilter filter =
        (EventFilter) p.getProperty(Properties.MOVE_EVENT_FILTER);
      if (filter == null || !filter.rejectEvent(e)) {
        selectionProcessor.accept(p);
      }
      else {
        DragBuffer.getBuffer().clear();
      }
    }
    else {
      DragBuffer.getBuffer().clear();
    }
    // show/hide selection boxes
    map.repaint();
  }

  /**
   * Checks if event has already been consumed -- so we will return false if this event
   * has already been handled by one of the fancier Drag Gesture Recognizers. Also screens
   * out double-clicks and anything with modifier keys down.
   * @param e mouse event
   * @return Should we treat this event as part of a "deep legacy" drag and drop?
   */
  protected boolean canHandleEvent(MouseEvent e) {
    return !e.isConsumed() &&
      !e.isShiftDown() &&
      !SwingUtils.isSelectionToggle(e) &&
      e.getClickCount() == 1 &&                   // Previously also allowed a clickCount of zero here, causing https://github.com/vassalengine/vassal/issues/12480
      (e.getButton() == MouseEvent.NOBUTTON ||
        SwingUtils.isMainMouseButtonDown(e));
  }

  /**
   * @return true if this point is "close enough" to the point at which
   * the user initially pressed the mouse button to be considered a mouse
   * click (such that no drag-and-drop moves are processed)
   */
  public boolean isClick(Point pt) {
    boolean isClick = false;
    if (dragBegin != null) {
      final Board b = map.findBoard(pt);
      boolean useGrid = b != null && b.getGrid() != null;
      if (useGrid) {
        final PieceIterator it = DragBuffer.getBuffer().getIterator();
        final GamePiece dragging = it.hasMoreElements() ? it.nextPiece() : null;
        useGrid =
          dragging != null &&
            !Boolean.TRUE.equals(dragging.getProperty(Properties.IGNORE_GRID)) &&
            (dragging.getParent() == null || !dragging.getParent().isExpanded());
      }

      if (useGrid) {
        if (map.equals(DragBuffer.getBuffer().getFromMap())) {
          if (map.snapTo(pt).equals(map.snapTo(dragBegin))) {
            isClick = true;
          }
        }
      }

      if (!breachedThreshold &&
        map.mapToComponent(Math.abs(pt.x - dragBegin.x - curPieceOffsetX)) <= GlobalOptions.getInstance().getDragThreshold() &&
        map.mapToComponent(Math.abs(pt.y - dragBegin.y - curPieceOffsetY)) <= GlobalOptions.getInstance().getDragThreshold()) {
        isClick = true;
      }
    }
    return isClick;
  }

  /**
   * Mouse button has been released -- if we can still handle the event (i.e. we haven't picked up some exotic
   * modifier key during the drag, etc), then we perform the drop.
   * @param e Mouse Event -> NOTE that the supplied point is not the actual mouse position but rather it has been offset by how far off-center the drag point on the piece was/is.
   */
  @Override
  public void mouseReleased(MouseEvent e) {
    if (canHandleEvent(e)) {
      if (!isClick(e.getPoint())) {
        performDrop(e.getPoint());
      }
    }
    dragBegin = null;
    breachedThreshold = false;
    map.getView().setCursor(null);
  }

  @Override
  public void mouseMoved(MouseEvent e) {
  }

  /**
   * Check if we have breached our drag threshold during a drag. Only meaningful in legacy DnD situations.
   * @param e mouse event.
   */
  @Override
  public void mouseDragged(MouseEvent e) {
    if (!breachedThreshold && (dragBegin != null)) {
      final Point pt = map.componentToMap(e.getPoint());
      if (map.mapToComponent(Math.abs(pt.x - dragBegin.x)) > GlobalOptions.getInstance().getDragThreshold() ||
        map.mapToComponent(Math.abs(pt.y - dragBegin.y)) > GlobalOptions.getInstance().getDragThreshold()) {
        breachedThreshold = true;
      }
    }
  }

  /**
   * Moves the group of dragged (in the DragBuffer) pieces to the target point (p).
   * @param p Point that mouse has been dragged to.
   */
  protected void performDrop(Point p) {
    final Command move = movePieces(map, p);
    GameModule.getGameModule().sendAndLog(move); // Sends the command over the wire (and/or into the logfile)
    if (move != null) {
      DragBuffer.getBuffer().clear(); // If we did anything, clear the drag buffer, as the DnD operation is complete.
      GameModule.getGameModule().refreshVisibleMaps(); // Force any changed labels to redisplay on visible maps.
    }
  }

  @Override
  public void mouseEntered(MouseEvent e) {
  }

  @Override
  public void mouseExited(MouseEvent e) {
  }

  @Override
  public void mouseClicked(MouseEvent e) {
  }

  /**
   * Implement Comparator to sort the contents of the drag buffer before
   * completing the drag. This sorts the contents to be in the same order
   * as the pieces were in their original parent stack.
   */
  @Override
  public int compare(GamePiece p1, GamePiece p2) {
    return pieceSorter.compare(p1, p2);
  }

  // We force the loading of these classes because otherwise they would
  // be loaded when the user initiates the first drag, which makes the
  // start of the drag choppy.
  static {
    try {
      Class.forName(MovementReporter.class.getName());
      Class.forName(KeyBuffer.class.getName());
    }
    catch (ClassNotFoundException e) {
      throw new IllegalStateException(e); // impossible
    }
  }

  /**
   * Common functionality for DragHandler for cases with and without drag
   * image support.
   *
   * @author Pieter Geerkens
   */
  public abstract static class AbstractDragHandler
    implements DragGestureListener,       DragSourceListener,
               DragSourceMotionListener,  DropTargetListener   {

    private static AbstractDragHandler theDragHandler = AbstractDragHandlerFactory.getCorrectDragHandler();

    /** returns the singleton DragHandler instance */
    public static AbstractDragHandler getTheDragHandler() {
      return theDragHandler;
    }

    public static void setTheDragHandler(AbstractDragHandler myHandler) {
      theDragHandler = myHandler;
    }

    /**
     * Picks the correct drag handler based on our OS, DragSource, and preferences.
     */
    public static class AbstractDragHandlerFactory {
      public static AbstractDragHandler getCorrectDragHandler() {
        if (!DragSource.isDragImageSupported() || GlobalOptions.getInstance().isForceNonNativeDrag()) {
          return new DragHandlerNoImage();
        }
        else {
          return SystemUtils.IS_OS_MAC ? new DragHandlerMacOSX() : new DragHandler();
        }
      }
    }

    /**
     * Finds all the piece slots in a module and resets their drop targets to use a new DragHandler
     * @param target recursive search through components
     */
    public static void resetRecursivePieceSlots(AbstractBuildable target) {
      for (final Buildable b : target.getBuildables()) {
        if (b instanceof PieceSlot) {
          final Component panel = ((PieceSlot)b).getComponent();
          panel.setDropTarget(makeDropTarget(panel, DnDConstants.ACTION_MOVE, null));
        }
        else if (b instanceof AbstractBuildable) {
          resetRecursivePieceSlots((AbstractBuildable)b);
        }
      }
    }

    /**
     * Reset our drag handler, e.g. if our preferences change.
     */
    public static void resetDragHandler() {
      final AbstractDragHandler newHandler = AbstractDragHandlerFactory.getCorrectDragHandler();
      setTheDragHandler(newHandler);
      for (final Map map : Map.getMapList()) {
        map.setDragGestureListener(newHandler);
        map.getComponent().setDropTarget(makeDropTarget(map.getComponent(), DnDConstants.ACTION_MOVE, map));
      }

      resetRecursivePieceSlots(GameModule.getGameModule());
    }

    /**
     * Registers a PieceMover
     * @param pm PieceMover for this dragHandler
     */
    public static void addPieceMover(PieceMover pm) {
      if (!pieceMovers.contains(pm)) {
        pieceMovers.add(pm);
      }
    }

    /**
     * Creates a new DropTarget and hooks us into the beginning of a
     * DropTargetListener chain. DropTarget events are not multicast;
     * there can be only one "true" listener.
     */
    public static DropTarget makeDropTarget(Component theComponent, int dndContants, DropTargetListener dropTargetListener) {
      if (dropTargetListener != null) {
        getTheDragHandler()
                   .dropTargetListeners.put(theComponent, dropTargetListener);
      }
      return new DropTarget(theComponent, dndContants, getTheDragHandler());
    }

    /**
     * Removes a dropTarget component
     * @param theComponent component to remove
     */
    public static void removeDropTarget(Component theComponent) {
      getTheDragHandler().dropTargetListeners.remove(theComponent);
    }

    private static StackMetrics getStackMetrics(GamePiece piece) {
      final Map map = piece.getMap();
      if (map != null) {
        final StackMetrics sm = map.getStackMetrics();
        if (sm != null) {
          return sm;
        }
      }
      return new StackMetrics();
    }

    protected static List<PieceMover> pieceMovers = new ArrayList<>(); // our piece movers

    protected static final int CURSOR_ALPHA = 127; // pseudo cursor is 50% transparent
    protected static final int EXTRA_BORDER = 4;   // pseudo cursor is includes a 4 pixel border

    protected Rectangle boundingBox;    // image bounds
    protected Rectangle boundingBoxComp;    // image bounds

    private int originalPieceOffsetX; // How far drag STARTED from GamePiece's center (on original map)
    private int originalPieceOffsetY;

    protected double dragPieceOffCenterZoom = 1.0; // zoom at start of drag

    protected int currentPieceOffsetX; // How far cursor is CURRENTLY off-center, a function of dragPieceOffCenter{X,Y,Zoom}
    protected int currentPieceOffsetY; // I.e. on current map (which may have different zoom)

    // Seems there can be only one DropTargetListener per drop target. After we
    // process a drop target event, we manually pass the event on to this listener.
    protected java.util.Map<Component, DropTargetListener> dropTargetListeners = new HashMap<>();

    // used by DragHandlerNoImage only
    protected Point lastDragLocation = new Point();

    // used by DragHandlerNoImage only
    protected JLabel dragCursor;      // An image label. Lives on current DropTarget's LayeredPane.

    // used by DragHandlerNoImage only
    protected double dragCursorZoom = 1.0; // Current cursor scale (zoom)

    /**
     * @return platform-dependent offset multiplier
     */
    protected abstract int getOffsetMult();

    /**
     * @param dge DG event
     * @return platform-dependent device scale
     */
    protected abstract double getDeviceScale(DragGestureEvent dge);

    protected abstract double getDeviceScale(DropTargetDragEvent e);

    /**
     * @param e DropTargetEvent
     * @return associated DropTargetListener
     */
    protected DropTargetListener getListener(DropTargetEvent e) {
      final Component component = e.getDropTargetContext().getComponent();
      return dropTargetListeners.get(component);
    }

    /**
     * Moves the drag cursor on the current draw window
     * @param dragX x position
     * @param dragY y position
     */
    @Deprecated(since = "2023-05-08", forRemoval = true)
    protected void moveDragCursor(int dragX, int dragY) {}

    /**
     * Removes the drag cursor from the current draw window
     */
    @Deprecated(since = "2023-05-08", forRemoval = true)
    protected void removeDragCursor() {}

    /** calculates the offset between cursor dragCursor positions */
    protected void calcDrawOffset() {}

    /**
     * creates or moves cursor object to given window. Called when drag
     * operation begins in a window or the cursor is dragged over a new
     * drop-target window
     *
     * @param newDropWin window component to be our new draw window.
     */
    @Deprecated(since = "2023-05-08", forRemoval = true)
    public void setDrawWinToOwnerOf(Component newDropWin) {}

    /**
     * Common functionality abstracted from makeDragImage and makeDragCursor
     *
     * @param zoom Zoom Level
     * @param doOffset Drag Offset
     * @param target Target Component
     * @param setSize Set Size
     * @return Drag Image
     */
    @Deprecated(since = "2023-05-08", forRemoval = true)
    protected BufferedImage makeDragImageCursorCommon(double zoom, boolean doOffset, Component target, boolean setSize) {
      return makeDragImageCursorCommon(zoom, 1.0, doOffset, target);
    }

    protected BufferedImage makeDragImageCursorCommon(double mapzoom, double os_scale, boolean doOffset, Component target) {
      // FIXME: Should be an ImageOp for caching?
      final double zoom = mapzoom * os_scale;

      currentPieceOffsetX =
        (int) (originalPieceOffsetX / dragPieceOffCenterZoom * mapzoom + 0.5);
      currentPieceOffsetY =
        (int) (originalPieceOffsetY / dragPieceOffCenterZoom * mapzoom + 0.5);

      final List<Point> relativePositions = buildBoundingBox();

      // convert boundingBoxComp to component space
      boundingBoxComp = new Rectangle(boundingBox);
      boundingBoxComp.width *= mapzoom;
      boundingBoxComp.height *= mapzoom;
      boundingBoxComp.x *= mapzoom;
      boundingBoxComp.y *= mapzoom;

      if (doOffset) {
        calcDrawOffset();
      }

      // convert boundingBox, relativePosisions to drawing space
      boundingBox.width *= zoom;
      boundingBox.height *= zoom;
      boundingBox.x *= zoom;
      boundingBox.y *= zoom;

      for (final Point p: relativePositions) {
        p.x *= zoom;
        p.y *= zoom;
      }

      final int w = boundingBox.width + EXTRA_BORDER * 2;
      final int h = boundingBox.height + EXTRA_BORDER * 2;

      final BufferedImage image = ImageUtils.createCompatibleTranslucentImage(w, h);
      drawDragImage(image, target, relativePositions, zoom);

      return image;
    }

    /**
     * Creates the image to use when dragging based on the zoom factor
     * passed in.
     *
     * @param zoom DragBuffer.getBuffer
     * @return dragImage
     */
    private BufferedImage makeDragImage(double mapzoom, double os_scale) {
      return makeDragImageCursorCommon(mapzoom, os_scale, false, null);
    }

    @Deprecated(since = "2023-05-08", forRemoval = true)
    protected void makeDragCursor(double zoom) {}

    private List<Point> buildBoundingBox() {
      // boundingBox and relativePositions are constructed in map
      // coordinates in this function

      final List<Point> relativePositions = new ArrayList<>();
      final PieceIterator dragContents = DragBuffer.getBuffer().getIterator();
      final GamePiece firstPiece = dragContents.nextPiece();
      GamePiece lastPiece = firstPiece;

      boundingBox = firstPiece.getShape().getBounds();
      relativePositions.add(new Point(0, 0));

      int stackCount = 0;
      while (dragContents.hasMoreElements()) {
        final GamePiece nextPiece = dragContents.nextPiece();
        final Rectangle r = nextPiece.getShape().getBounds();

        final Point p = new Point(
          nextPiece.getPosition().x - firstPiece.getPosition().x,
          nextPiece.getPosition().y - firstPiece.getPosition().y
        );
        r.translate(p.x, p.y);

        if (nextPiece.getPosition().equals(lastPiece.getPosition())) {
          stackCount++;
          final StackMetrics sm = getStackMetrics(nextPiece);
          r.translate(
            sm.unexSepX * stackCount,
            -sm.unexSepY * stackCount
          );
        }

        boundingBox.add(r);
        relativePositions.add(p);
        lastPiece = nextPiece;
      }

      return relativePositions;
    }

    private void drawDragImage(BufferedImage image, Component target,
                               List<Point> relativePositions, double zoom) {
      final Graphics2D g = image.createGraphics();
      g.addRenderingHints(SwingUtils.FONT_HINTS);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                         RenderingHints.VALUE_ANTIALIAS_ON);

      int index = 0;
      Point lastPos = null;
      int stackCount = 0;
      for (final PieceIterator dragContents = DragBuffer.getBuffer().getIterator();
           dragContents.hasMoreElements(); ) {

        final GamePiece piece = dragContents.nextPiece();
        final Point pos = relativePositions.get(index++);
        final Map map = piece.getMap();

        if (piece instanceof Stack) {
          stackCount = 0;
          piece.draw(g, EXTRA_BORDER - boundingBox.x + pos.x,
                        EXTRA_BORDER - boundingBox.y + pos.y,
                        map == null ? target : map.getView(), zoom);
        }
        else {
          final Point offset = new Point(0, 0);
          if (pos.equals(lastPos)) {
            stackCount++;
            final StackMetrics sm = getStackMetrics(piece);
            offset.x = (int) Math.round(sm.unexSepX * stackCount * zoom);
            offset.y = (int) Math.round(sm.unexSepY * stackCount * zoom);
          }
          else {
            stackCount = 0;
          }

          final int x = EXTRA_BORDER - boundingBox.x + pos.x + offset.x;
          final int y = EXTRA_BORDER - boundingBox.y + pos.y - offset.y;

          String owner = "";
          final GamePiece parent = piece.getParent();
          boolean faceDown = false;
          if (parent instanceof Deck) {
            owner = (String)piece.getProperty(Properties.OBSCURED_BY);
            faceDown = ((Deck) parent).isFaceDown();
            piece.setProperty(Properties.OBSCURED_BY, faceDown ? Deck.NO_USER : null);
            if (faceDown) {
              piece.setProperty(Properties.USE_UNROTATED_SHAPE, Boolean.TRUE);
            }
          }

          final AffineTransform t = AffineTransform.getScaleInstance(zoom, zoom);
          t.translate(x / zoom, y / zoom);
          g.setClip(t.createTransformedShape(piece.getShape()));

          piece.draw(g, x, y, map == null ? target : map.getView(), zoom);

          g.setClip(null);

          final Highlighter highlighter = map == null ?
            BasicPiece.getHighlighter() : map.getHighlighter();
          highlighter.draw(piece, g, x, y, null, zoom);

          if (piece.getParent() instanceof Deck) {
            piece.setProperty(Properties.OBSCURED_BY, owner);
            if (faceDown) {
              piece.setProperty(Properties.USE_UNROTATED_SHAPE, Boolean.FALSE);
            }
          }

          final Mat mat = (Mat) Decorator.getDecorator(piece, Mat.class);
          if (mat != null) {
            mat.drawCargo(g, x, y, null, zoom);
          }
        }

        lastPos = pos;
      }

      // make the drag image transparent
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_IN));
      g.setColor(new Color(0xFF, 0xFF, 0xFF, CURSOR_ALPHA));
      g.fillRect(0, 0, image.getWidth(), image.getHeight());

      g.dispose();
    }

    /******************************************************************************
     * DRAG GESTURE LISTENER INTERFACE
     *
     * EVENT uses SCALED, DRAG-SOURCE coordinate system. ("component coordinates")
     * PIECE uses SCALED, OWNER (arbitrary) coordinate system ("map coordinates")
     *
     * Fires after user begins moving the mouse several pixels over a map. This
     * method will be overridden, but called as a super(), by the Drag Gesture
     * extension that is used, which will either be {@link DragHandler} if DragImage
     * is supported by the JRE, or {@link DragHandlerNoImage} if not. Either one will
     * have called {@link dragGestureRecognizedPrep}, immediately below, before it
     * calls this method.
     ******************************************************************************/
    @Override
    public void dragGestureRecognized(DragGestureEvent dge) {
      try {
        beginDragging(dge);
      }
      // FIXME: Fix by replacing AWT Drag 'n Drop with Swing DnD.
      // Catch and ignore spurious DragGestures
      catch (InvalidDnDOperationException ignored) {
      }
    }

    /**
     * Sets things up at the beginning of a drag-and-drop operation:
     * <br> - Screen out any immovable pieces
     * <br> - Account for any offsets on in the window
     * <br> - Sets dragWin to our source window
     *
     * @param dge dg event
     * @return mousePosition if we processed, or null if we bailed.
     */
    protected Point dragGestureRecognizedPrep(DragGestureEvent dge) {
      // Ensure the user has dragged on a counter before starting the drag.
      final DragBuffer db = DragBuffer.getBuffer();
      if (db.isEmpty()) return null;

      // Remove any Immovable pieces from the DragBuffer that were
      // selected in a selection rectangle, unless they are being
      // dragged from a piece palette (i.e., getMap() == null).
      final List<GamePiece> pieces = new ArrayList<>();
      for (final PieceIterator i = db.getIterator();  // NOPMD
           i.hasMoreElements(); pieces.add(i.nextPiece()));
      for (final GamePiece piece : pieces) {
        if (piece.getMap() != null &&
            Boolean.TRUE.equals(piece.getProperty(Properties.NON_MOVABLE))) {
          db.remove(piece);
        }
      }

      // Bail out if this leaves no pieces to drag.
      if (db.isEmpty()) return null;

      final GamePiece piece = db.getIterator().nextPiece();

      final Map map = dge.getComponent() instanceof Map.View ?
                      ((Map.View) dge.getComponent()).getMap() : null;

      final Point mousePosition = dge.getDragOrigin(); //BR// Bug13137 - now that we're not pre-adulterating dge's event, it already arrives in component coordinates

      Point piecePosition = piece.getPosition();

      // If DragBuffer holds a piece with invalid coordinates (for example, a
      // card drawn from a deck), drag from center of piece
      if (piecePosition.x <= 0 || piecePosition.y <= 0) {
        piecePosition = map == null ? mousePosition :
                                      map.componentToMap(mousePosition);
      }

      // If coming from a map, we use the map's zoom. Otherwise if our
      // PieceWindow has stashed a starting scale for us then use that, else 1.0
      if (map != null) {
        // Account for offset of piece within stack. We do this even for
        // un-expanded stacks, since the offset can still be significant if
        // the stack is large
        final Stack parent = piece.getParent();
        if (parent != null) {
          final Point offset = parent.getStackMetrics()
                                     .relativePosition(parent, piece);
          piecePosition.translate(offset.x, offset.y);
        }

        piecePosition = map.mapToComponent(piecePosition);

        dragPieceOffCenterZoom = map.getZoom();
      }
      else {
        // NB: In the case where there is no map, piecePosition is already
        // in the component coordinate system, so we don't convert here.

        final Object tempZoom = piece.getProperty(PieceSlot.PIECE_PALETTE_SCALE);
        if (tempZoom != null) {
          final BasicPiece bp = (BasicPiece)Decorator.getInnermost(piece);
          bp.setPersistentProperty(PieceSlot.PIECE_PALETTE_SCALE, null);

          if (tempZoom instanceof Double) {
            dragPieceOffCenterZoom = (Double)tempZoom;
          }
          else {
            dragPieceOffCenterZoom = Double.parseDouble((String)tempZoom);
          }
        }
        else {
          dragPieceOffCenterZoom = 1.0;
        }
      }

      // dragging from UL results in positive offsets
      originalPieceOffsetX = piecePosition.x - mousePosition.x;
      originalPieceOffsetY = piecePosition.y - mousePosition.y;

      return mousePosition;
    }

    /**
     * The the Drag Gesture Recognizer that we're officially beginning a drag.
     * @param dge DG event
     */
    protected void beginDragging(DragGestureEvent dge) {
      final double os_scale = getDeviceScale(dge);

      // this call is needed to instantiate the boundingBox object
      final BufferedImage bImage = makeDragImage(dragPieceOffCenterZoom, os_scale);

      final Point dragPointOffset = new Point(
        (int) Math.round(getOffsetMult() * ((boundingBoxComp.x + currentPieceOffsetX) * os_scale - EXTRA_BORDER)),
        (int) Math.round(getOffsetMult() * ((boundingBoxComp.y + currentPieceOffsetY) * os_scale - EXTRA_BORDER))
      );

      //BR// Inform PieceMovers of relevant metrics
      for (final PieceMover pieceMover : pieceMovers) {
        pieceMover.setCurPieceOffset(currentPieceOffsetX, currentPieceOffsetY);
        pieceMover.setBreachedThreshold(true);
      }

      dge.startDrag(
        Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
        GlobalOptions.getInstance().isForceNonNativeDrag() ? null : bImage,
        dragPointOffset,
        new StringSelection(""),
        this
      );

      dge.getDragSource().addDragSourceMotionListener(this);

      // Let our map's KeyBufferer know that this is now a drag not a click.
      final Map map = dge.getComponent() instanceof Map.View ?
        ((Map.View) dge.getComponent()).getMap() : null;
      if (map != null) {
        final KeyBufferer kb = map.getKeyBufferer();
        if (kb != null) {
          kb.dragStarted();
        }
      }
    }

    /**************************************************************************
     * DRAG SOURCE LISTENER INTERFACE
     * @param e DragSourceDropEvent
     **************************************************************************/
    @Override
    public void dragDropEnd(DragSourceDropEvent e) {
      final DragSource ds = e.getDragSourceContext().getDragSource();
      ds.removeDragSourceMotionListener(this);
    }

    @Override
    public void dragEnter(DragSourceDragEvent e) {}

    @Override
    public void dragExit(DragSourceEvent e) {}

    @Override
    public void dragOver(DragSourceDragEvent e) {}

    @Override
    public void dropActionChanged(DragSourceDragEvent e) {}

    /*************************************************************************
     * DRAG SOURCE MOTION LISTENER INTERFACE
     *
     * EVENT uses UNSCALED, SCREEN coordinate system
     *
     * Moves cursor after mouse. Used to check for real mouse movement.
     * Warning: dragMouseMoved fires 8 times for each point on development
     * system (Win2k)
     ************************************************************************/
    @Override
    public void dragMouseMoved(DragSourceDragEvent dsde) {
      if (dsde.getDragSourceContext().getComponent() instanceof Map.View) {
        final Map map = ((Map.View) dsde.getDragSourceContext().getComponent()).getMap();

        Point pt = dsde.getLocation();
        SwingUtilities.convertPointFromScreen(pt, map.getComponent());
        pt.translate(currentPieceOffsetX, currentPieceOffsetY);
        pt = map.componentToMap(pt);

        final DebugControls dc = GameModule.getGameModule().getDebugControls();
        dc.setCursorLocation(pt, map);
      }
    }

    /**************************************************************************
     * DROP TARGET INTERFACE
     *
     * EVENT uses UNSCALED, DROP-TARGET coordinate system
     *
     * dragEnter - switches current drawWin when mouse enters a new DropTarget
     **************************************************************************/
    @Override
    public void dragEnter(DropTargetDragEvent e) {
      final DropTargetListener forward = getListener(e);
      if (forward != null) {
        forward.dragEnter(e);
      }
    }

    /**************************************************************************
     * DROP TARGET INTERFACE
     *
     * EVENT uses UNSCALED, DROP-TARGET coordinate system
     *
     * drop() - Last event of the drop operation. We adjust the drop point for
     * off-center drag, remove the cursor, and pass the event along
     * listener chain.
     **************************************************************************/
    @Override
    public void drop(DropTargetDropEvent e) {
      // EVENT uses UNSCALED, DROP-TARGET coordinate system
      e.getLocation().translate(currentPieceOffsetX, currentPieceOffsetY);
      final DropTargetListener forward = getListener(e);
      if (forward != null) {
        forward.drop(e);
      }
    }

    /** Passes event along listener chain */
    @Override
    public void dragExit(DropTargetEvent e) {
      final DropTargetListener forward = getListener(e);
      if (forward != null) forward.dragExit(e);
    }

    /** Passes event along listener chain */
    @Override
    public void dragOver(DropTargetDragEvent e) {
      final DropTargetListener forward = getListener(e);
      if (forward != null) forward.dragOver(e);
    }

    /** Passes event along listener chain */
    @Override
    public void dropActionChanged(DropTargetDragEvent e) {
      final DropTargetListener forward = getListener(e);
      if (forward != null) forward.dropActionChanged(e);
    }
  }

  /**
   * VASSAL's front-line drag handler for drag-and-drop of pieces.
   *
   * Implementation of AbstractDragHandler when DragImage is supported by JRE.
   * {@link DragHandlerMacOSX} extends this for special Mac platform
   *
   * @author Pieter Geerkens
   */
  public static class DragHandler extends AbstractDragHandler {
    @Override
    public void dragGestureRecognized(DragGestureEvent dge) {
      if (dragGestureRecognizedPrep(dge) != null) {
        super.dragGestureRecognized(dge);
      }
    }

    @Override
    protected int getOffsetMult() {
      return -1;
    }

    @Override
    protected double getDeviceScale(DragGestureEvent dge) {
      // Get the OS scaling; note that this handler is _probably_ running only
      // on Windows.
      final Graphics2D g2d = (Graphics2D) dge.getComponent().getGraphics();
      final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
      g2d.dispose();
      return os_scale;
    }

    @Override
    protected double getDeviceScale(DropTargetDragEvent e) {
      // Get the OS scaling; note that this handler is _probably_ running only
      // on Windows.
      final Graphics2D g2d = (Graphics2D) e.getDropTargetContext().getComponent().getGraphics();
      final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
      g2d.dispose();
      return os_scale;
    }
  }

  /**
   * Special MacOSX variant of DragHandler, because of differences in how
   * device scaling is handled.
   */
  public static class DragHandlerMacOSX extends DragHandler {
    @Override
    protected int getOffsetMult() {
      return 1;
    }

    @Override
    protected double getDeviceScale(DragGestureEvent dge) {
      // Retina Macs account for the device scaling for the drag icon,
      // so we don't have to.
      return 1.0;
    }

    @Override
    protected double getDeviceScale(DropTargetDragEvent e) {
      return 1.0;
    }
  }

  /**
   * Fallback drag-handler when DragImage not supported by JRE. Implements a
   * pseudo-cursor that follows the mouse cursor when user drags game pieces.
   * Supports map zoom by resizing cursor when it enters a drop target of
   * type Map.View.
   * <br>
   * @author Jim Urbas
   * @version 0.4.2
   */
  public static class DragHandlerNoImage extends AbstractDragHandler {
    protected Component dragWin; // the component that initiated the drag operation
    protected Component dropWin; // the drop target the mouse is currently over
    protected JLayeredPane drawWin; // the component that owns our pseudo-cursor

    protected final Point drawOffset = new Point(); // translates event coords to local drawing coords

    @Override
    public void dragGestureRecognized(DragGestureEvent dge) {
      final Point mousePosition = dragGestureRecognizedPrep(dge);
      if (mousePosition == null) return;

      dragWin = dge.getComponent();
      drawWin = null;
      dropWin = null;

      makeDragCursor(dragPieceOffCenterZoom, getDeviceScale(dge));
      setDrawWinToOwnerOf(dragWin);
      SwingUtilities.convertPointToScreen(mousePosition, drawWin);
      moveDragCursor(mousePosition.x, mousePosition.y);

      super.dragGestureRecognized(dge);
    }

    @Override
    @Deprecated(since = "2023-05-15", forRemoval = true)
    protected void makeDragCursor(double zoom) {
      makeDragCursor(zoom, 1.0);
    }

    /**
     * Installs the cursor image into our dragCursor JLabel.
     * Sets current zoom. Should be called at beginning of drag
     * and whenever zoom changes. INPUT: DragBuffer.getBuffer OUTPUT:
     * dragCursorZoom cursorOffCenterX cursorOffCenterY boundingBox
     * @param zoom DragBuffer.getBuffer
     *
     */
    protected void makeDragCursor(double zoom, double os_scale) {
      // create the cursor if necessary
      if (dragCursor == null) {
        dragCursor = new JLabel();
        dragCursor.setVisible(false);
      }

      final BufferedImage img = makeDragImageCursorCommon(zoom, os_scale, true, dragCursor);
      dragCursor.setSize(img.getWidth(), img.getHeight());
      dragCursor.setIcon(new ImageIcon(img));
      dragCursorZoom = zoom;
    }

    /**
     * Moves the drag cursor on the current draw window
     * @param dragX x position
     * @param dragY y position
     */
    @Override
    protected void moveDragCursor(int dragX, int dragY) {
      if (drawWin != null) {
        dragCursor.setLocation(dragX - drawOffset.x, dragY - drawOffset.y);
      }
    }

    /**
     * Removes the drag cursor from the current draw window
     */
    @Override
    protected void removeDragCursor() {
      if (drawWin != null) {
        if (dragCursor != null) {
          dragCursor.setVisible(false);
          drawWin.remove(dragCursor);
        }
        drawWin = null;
      }
    }

    /**
     * creates or moves cursor object to given JLayeredPane. Usually called by setDrawWinToOwnerOf()
     * @param newDrawWin JLayeredPane that is to be our new drawWin
     */
    private void setDrawWin(JLayeredPane newDrawWin) {
      if (newDrawWin != drawWin) {
        // remove cursor from old window
        if (dragCursor.getParent() != null) {
          dragCursor.getParent().remove(dragCursor);
        }
        if (drawWin != null) {
          drawWin.repaint(dragCursor.getBounds());
        }
        drawWin = newDrawWin;
        calcDrawOffset();
        dragCursor.setVisible(false);
        drawWin.add(dragCursor, JLayeredPane.DRAG_LAYER);
      }
    }

    /** calculates the offset between cursor dragCursor positions */
    @Override
    protected void calcDrawOffset() {
      if (drawWin != null) {
        // drawOffset is the offset between the mouse location during a drag
        // and the upper-left corner of the cursor
        // accounts for difference between event point (screen coords)
        // and Layered Pane position, boundingBox and off-center drag
        drawOffset.x = -boundingBoxComp.x - currentPieceOffsetX + EXTRA_BORDER;
        drawOffset.y = -boundingBoxComp.y - currentPieceOffsetY + EXTRA_BORDER;
        SwingUtilities.convertPointToScreen(drawOffset, drawWin);
      }
    }

    /**
     * creates or moves cursor object to given window. Called when drag operation begins in a window or the cursor is
     * dragged over a new drop-target window
     * @param newDropWin window component to be our new draw window.
     */
    @Override
    public void setDrawWinToOwnerOf(Component newDropWin) {
      if (newDropWin != null) {
        final JRootPane rootWin = SwingUtilities.getRootPane(newDropWin);
        if (rootWin != null) {
          setDrawWin(rootWin.getLayeredPane());
        }
      }
    }

    @Override
    protected int getOffsetMult() {
      return 1;
    }

    @Override
    protected double getDeviceScale(DragGestureEvent dge) {
      return 1.0;
    }

    @Override
    protected double getDeviceScale(DropTargetDragEvent e) {
      return 1.0;
    }

    @Override
    public void dragDropEnd(DragSourceDropEvent e) {
      removeDragCursor();
      super.dragDropEnd(e);
    }

    @Override
    public void dragMouseMoved(DragSourceDragEvent e) {
      super.dragMouseMoved(e);
      if (!e.getLocation().equals(lastDragLocation)) {
        lastDragLocation = e.getLocation();

        moveDragCursor(e.getX(), e.getY());
        if (dragCursor != null && !dragCursor.isVisible()) {
          dragCursor.setVisible(true);
        }
      }
    }

    @Override
    public void dragEnter(DropTargetDragEvent e) {
      final Component newDropWin = e.getDropTargetContext().getComponent();
      if (newDropWin != dropWin) {
        final double newZoom = newDropWin instanceof Map.View
          ? ((Map.View) newDropWin).getMap().getZoom() : 1.0;
        if (Math.abs(newZoom - dragCursorZoom) > 0.01) {
          makeDragCursor(newZoom, getDeviceScale(e));
        }
        setDrawWinToOwnerOf(e.getDropTargetContext().getComponent());
        dropWin = newDropWin;
      }
      super.dragEnter(e);
    }

    @Override
    public void drop(DropTargetDropEvent e) {
      removeDragCursor();
      super.drop(e);
    }
  }

  private class MatMover extends MatHolder {

    public MatMover(GamePiece piece) {
      super(piece);
    }

    @Override public void grabCargo(List<GamePiece> allCargo) {
      super.grabCargo(allCargo);
      // Re-order the cargo by stack by position in stack so that we can move
      // it in the correct order to maintain stacking order.
      final List<GamePiece> tempCargo = new ArrayList<>();
      for (final GamePiece c : getCargo()) {
        final GamePiece parent = c.getParent();
        // If this piece belongs to a Stack and does not already exist in tempCargo, then
        // copy the entire contents of the Stack to tempCargo bottom up
        if (parent instanceof Stack) {
          if (!tempCargo.contains(c)) {
            final Stack s = (Stack) parent;
            for (int i = 0; i < s.getPieceCount(); i++) {
              tempCargo.add(s.getPieceAt(i));
            }
          }
        }
        // Not in a Stack, just copy it.
        else {
          tempCargo.add(c);
        }
      }
      setCargo(tempCargo);
    }
  }

  @Override
  public boolean isMandatory() {
    return true;
  }

  @Override
  public boolean isUnique() {
    return true;
  }
}
