package VASSAL.build.module;

import VASSAL.build.GameModule;
import VASSAL.chat.DummyClient;
import VASSAL.chat.DynamicClient;
import VASSAL.chat.node.NodeClient;
import VASSAL.chat.peer2peer.ClientTest;
import VASSAL.command.Command;
import VASSAL.command.CommandEncoder;
import VASSAL.command.NullCommand;
import VASSAL.tools.RecursionLimitException;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class BasicLoggerTest {

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
    }

    for (char action : actions.toCharArray()) {
      switch (action) {
        case 'S':
          logger.step();
          // simulate a call to sendAndLog
          logger.log(logger.logInput.get(logger.nextInput-1));
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

  private void VerifySequence(String actions, BasicLogger logger, GameModule gm) {
    char letter = 'A';
    char number = '1';
    int inputIndex = 0;
    int outputIndex = 0;
    Command c;

    for (char action : actions.toCharArray()) {
      switch (action) {
        case 'S':
          assertEquals(logger.logInput.get(inputIndex++), logger.logOutput.get(outputIndex++));
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

  @Test
  public void undoRedo() {
    try (MockedStatic<GameModule> staticGm = Mockito.mockStatic(GameModule.class)) {
      final GameModule gm = mock(GameModule.class);
      staticGm.when(GameModule::getGameModule).thenReturn(gm);
      BasicLogger logger = new BasicLogger();

      GameState gameState = new GameState();
      staticGm.when(gm::getGameState).thenReturn(gameState);
      ServerConnection server = new NodeClient("moduleName", "playerId", logger, "host", 0, null);
      staticGm.when(gm::getServer).thenReturn(server);

      String actions = "SSSUULUUX";
      LogSequence(actions, logger, gm);

//      ArgumentCaptor<Command> sendAndLogCaptor = ArgumentCaptor.forClass(Command.class);
//      verify(gm, times(8)).sendAndLog(sendAndLogCaptor.capture());
//      final List<Command> commands = sendAndLogCaptor.getAllValues();
      assertEquals(8, logger.logOutput.size());
      VerifySequence(actions, logger, gm);
    }
  }

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
      // Capture the sendAndLog parameter.
      verify(gm, times(index)).sendAndLog(sendAndLogCaptor.capture());
      final List<Command> commands = sendAndLogCaptor.getAllValues();
      for (int i=0; i<index; i++) {
        assertEquals(logger.logInput.get(i), commands.get(i));
      }
    }
  }

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
}

