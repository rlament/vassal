package VASSAL.build.module;

import VASSAL.build.GameModule;
import VASSAL.chat.node.NodeClient;
import VASSAL.command.Command;
import VASSAL.tools.RecursionLimitException;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BasicLoggerTest {

  /**
   * A helper function to simulate complex step/undo/redo sequences.
   * Populates the logger with a sequence of actions and then steps through those actions
   * to log the corresponding commands. Actions can consist of a mix of log file steps,
   * locally generated commands and undo. Players can interact with the module during playback.
   * This sequencer can simulate the injection of user commands during log file playback.
   * @param actions A string defining a sequence of actions, where S is a step action,
   *                Step actions a represented as single alphanumeric characters.
   *                L is a user generated loggable local action represented as numbers.
   *                U represents undo actions. X are extra step actions which are ignored
   *                during the verification phase.
   * @param logger This function adds the sequence to this parameter's logInput member.
   *               Adds commands during stepping to the outputLog.
   * @param gm A GameModule mock to generate Chatter commands.
   */
  private void LogSequence(String actions, BasicLogger logger, GameModule gm) {
    char letter = 'A';
    char number = '1';
    Command c;
    // Setup log file input
    for (char action : actions.toCharArray()) {
      if (action == 'S' || action == 'X') {
        c = new Chatter.DisplayText(gm.getChatter(), Character.toString(letter));
        logger.logInput.add(c);
        letter++;
      }
      else if (action == 'U' && letter > 'A') {
        --letter;
      }
    }

    for (char action : actions.toCharArray()) {
      switch (action) {
        case 'S':
          logger.step();
          break;
        case 'L' :
          c = new Chatter.DisplayText(gm.getChatter(), Character.toString(number));
          logger.log(c);
          number++;
          break;
        case 'U':
          logger.undo();
          break;
        default:
          break;
      }
    }
  }

  /**
   * Verify that the logOutput contains the expected actions/commands.
   * @param actions A string definition the expected actions. See LogSequence().
   * @param logger The logger containing the logOutput to be verified.
   * @param gm A GameModule mock to generate Chatter commands.
   */
  private void VerifySequence(String actions, BasicLogger logger, GameModule gm) {
    char letter = 'A';
    char number = '1';
    int inputIndex = 0;
    int outputIndex = 0;
    Command c;

    for (char action : actions.toCharArray()) {
      switch (action) {
        case 'S':
          assertEquals(logger.logInput.get(inputIndex++).toString(), logger.logOutput.get(outputIndex++).toString());
          break;
        case 'L':
          c = new Chatter.DisplayText(gm.getChatter(), Character.toString(number++));
          assertEquals(c.toString(), logger.logOutput.get(outputIndex++).toString());
          break;
        case 'U':
          outputIndex++;
          break;
      }
    }
  }

  /**
   * Step through input, with undo and intersperse a locally generated command.
   */
  @Test
  public void stepAndUndoExpectMatchingOutput() {
    try (MockedStatic<GameModule> staticGm = Mockito.mockStatic(GameModule.class)) {
      final GameModule gm = mock(GameModule.class);
      staticGm.when(GameModule::getGameModule).thenReturn(gm);
      BasicLogger logger = new BasicLogger();

      GameState gameState = new GameState();
      staticGm.when(gm::getGameState).thenReturn(gameState);
      ServerConnection server = new NodeClient("moduleName", "playerId", logger, "host", 0, null);
      staticGm.when(gm::getServer).thenReturn(server);

      // Mock sendAndLog removing the send while retaining the logging portion.
      staticGm.when(()->gm.sendAndLog(any(Command.class))).thenAnswer(new Answer<Object>() {
        @Override
        public Object answer(InvocationOnMock input) throws Throwable {
          logger.log(input.getArgument(0));
          return null;
        }
      });

      String actions = "SSSUULUUX";
      LogSequence(actions, logger, gm);

      assertEquals(8, logger.logOutput.size());
      VerifySequence(actions, logger, gm);
    }
  }

  /**
   * Step through log with undo and redo.
   */
  @Test
  public void stepUndoRedoExpectMatchingOutput() {
    try (MockedStatic<GameModule> staticGm = Mockito.mockStatic(GameModule.class)) {
      final GameModule gm = mock(GameModule.class);
      staticGm.when(GameModule::getGameModule).thenReturn(gm);
      BasicLogger logger = new BasicLogger();

      GameState gameState = new GameState();
      staticGm.when(gm::getGameState).thenReturn(gameState);
      ServerConnection server = new NodeClient("moduleName", "playerId", logger, "host", 0, null);
      staticGm.when(gm::getServer).thenReturn(server);

      // Mock sendAndLog removing the send while retaining the logging portion.
      staticGm.when(()->gm.sendAndLog(any(Command.class))).thenAnswer(new Answer<Object>() {
        @Override
        public Object answer(InvocationOnMock input) throws Throwable {
          logger.log(input.getArgument(0));
          return null;
        }
      });

      String actions = "SSSUUSS";
      LogSequence(actions, logger, gm);

      // Check output count and contents.
      assertEquals(actions.length(), logger.logOutput.size());
      VerifySequence(actions, logger, gm);
    }
  }

  /**
   * Step through a complete log file.
   */
  @Test
  public void stepThroughLogExpectMatchingOutput() throws RecursionLimitException, IOException {
    try (MockedStatic<GameModule> staticGm = Mockito.mockStatic(GameModule.class)) {
      final GameModule gm = mock(GameModule.class);
      staticGm.when(GameModule::getGameModule).thenReturn(gm);
      GameState gameState = new GameState();
      staticGm.when(gm::getGameState).thenReturn(gameState);

      // Capture the argument for the sendAndLog calls.
      ArgumentCaptor<Command> sendAndLogCaptor = ArgumentCaptor.forClass(Command.class);

      BasicLogger logger = new BasicLogger();

      char letter = 'A';
      for (int i=0; i<4; i++) {
        final Command c = new Chatter.DisplayText(gm.getChatter(), Character.toString(letter));
        letter++;
        logger.logInput.add(c);
      }

      int index = 0;
      while (logger.isReplaying()) {
        logger.step();
        index++;
      }
      // Capture the sendAndLog output.
      verify(gm, times(index)).sendAndLog(sendAndLogCaptor.capture());
      final List<Command> commands = sendAndLogCaptor.getAllValues();
      // Compare logInput to the commands output.
      for (int i=0; i<index; i++) {
        assertEquals(logger.logInput.get(i), commands.get(i));
      }
    }
  }

  /**
   * Step through log file, then undo all checking for a proper undo sequence.
   */
  @Test
  public void outputLogExpectUndoInReverseOrder() throws RecursionLimitException, IOException {
    try (MockedStatic<GameModule> staticGm = Mockito.mockStatic(GameModule.class)) {
      final GameModule gm = mock(GameModule.class);
      staticGm.when(GameModule::getGameModule).thenReturn(gm);
      BasicLogger logger = new BasicLogger();

      ServerConnection server= new NodeClient("moduleName", "playerId", logger,  "host", 0, null);
      staticGm.when(gm::getServer).thenReturn(server);

      // Capture the argument for the sendAndLog calls.
      //ArgumentCaptor<Command> sendAndLogCaptor = ArgumentCaptor.forClass(Command.class);

      char letter = 'A';
      for (int i=0; i<4; i++) {
        final Command c = new Chatter.DisplayText(gm.getChatter(), Character.toString(letter));
        letter++;
        logger.log(c);
      }

      final int cmdCount = logger.logOutput.size();
      for (int i=0; i<cmdCount; i++) {
        logger.undo();
      }

      // one extra to confirm it is ignored.
      logger.undo();

      assertEquals(8, logger.logOutput.size());

      // Regex pattern to match the last char within the square brackets.
      Pattern p = Pattern.compile("\\[.*(.)]");
      // Verify that undo commands are in reverse order.
      // First half of list contains the commands, second half the undo commands.
      // For example: comparing [A] to [* UNDO: A] is considered a match.
      final int size = logger.logOutput.size();
      for (int i=0; i<size/2; i++) {
        Matcher m1 = p.matcher(logger.logOutput.get(i).toString());
        Matcher m2 = p.matcher(logger.logOutput.get(size-i-1).toString());
        assertTrue(m1.find());
        assertTrue(m2.find());
        assertEquals(m1.group(1), m2.group(1));
      }
    }
  }

  /**
   * Step through and then fully undo a log file containing Undo commands.
   * The outputLog should contain double the log file commands with the
   * same number of step and undo commands.
   * @throws RecursionLimitException
   * @throws IOException
   */
  @Test
  public void processUndoDuringLogStepping() throws RecursionLimitException, IOException {
    try (MockedStatic<GameModule> staticGm = Mockito.mockStatic(GameModule.class)) {
      final GameModule gm = mock(GameModule.class);
      staticGm.when(GameModule::getGameModule).thenReturn(gm);

      BasicLogger logger = new BasicLogger();

      GameState gameState = new GameState();
      staticGm.when(gm::getGameState).thenReturn(gameState);
      ServerConnection server = new NodeClient("moduleName", "playerId", logger, "host", 0, null);
      staticGm.when(gm::getServer).thenReturn(server);

      // Mock sendAndLog() bypassing the send while retaining the logging portion.
      // This adds the command to logOutput.
      staticGm.when(()->gm.sendAndLog(any(Command.class))).thenAnswer(new Answer<Object>() {
        @Override
        public Object answer(InvocationOnMock input) throws Throwable {
          logger.log(input.getArgument(0));
          return null;
        }
      });

      // Simulate a simple log file with some display text and an undo.
      logger.logInput.add(new Chatter.DisplayText(gm.getChatter(),"First message"));
      logger.logInput.add(
              new BasicLogger.UndoCommand(true)
                      .append(new Chatter.DisplayText(gm.getChatter(),"logged undo"))
                      .append((new BasicLogger.UndoCommand(false))));
      logger.step();
      logger.step();
      logger.undo();
      logger.undo();

      assertEquals(logger.logInput.size() * 2, logger.logOutput.size());
     }
  }

  @Test
  public void stepUndoRedoExpectCorrectButtonStates() throws RecursionLimitException, IOException {
    try (MockedStatic<GameModule> staticGm = Mockito.mockStatic(GameModule.class)) {
      final GameModule gm = mock(GameModule.class);
      staticGm.when(GameModule::getGameModule).thenReturn(gm);

      BasicLogger logger = new BasicLogger();

      GameState gameState = new GameState();
      staticGm.when(gm::getGameState).thenReturn(gameState);
      ServerConnection server = new NodeClient("moduleName", "playerId", logger, "host", 0, null);
      staticGm.when(gm::getServer).thenReturn(server);

      // Mock sendAndLog() bypassing the send while retaining the logging portion.
      // This adds the command to logOutput.
      staticGm.when(()->gm.sendAndLog(any(Command.class))).thenAnswer(new Answer<Object>() {
        @Override
        public Object answer(InvocationOnMock input) throws Throwable {
          logger.log(input.getArgument(0));
          return null;
        }
      });

      // Simulate a simple log file with some display text and an undo.
      logger.logInput.add(new Chatter.DisplayText(gm.getChatter(),"OutputLog message"));
      logger.logInput.add(
              new BasicLogger.UndoCommand(true)
                      .append(new Chatter.DisplayText(gm.getChatter(),"logged undo"))
                      .append((new BasicLogger.UndoCommand(false))));

      // Inject a user action, prior to log playback, to offset input and output logs.
      logger.logOutput.add(new Chatter.DisplayText(gm.getChatter(),"User Action"));

      logger.step();
      logger.step();
      assertFalse(logger.isReplaying());  // at end of log file
      logger.undo();
      assertTrue(logger.isReplaying());
      logger.undo();
      logger.undo();  // Undo OutputLog message

      logger.step();  // Replay log file
      logger.step();
      assertFalse(logger.isReplaying());  // at end of log file
      logger.undo();
      logger.undo();
      assertFalse(logger.undoAction.isEnabled());
    }
  }
}
