package VASSAL.build.module;

import VASSAL.build.Buildable;
import VASSAL.build.GameModule;
import VASSAL.build.MockModuleTest;
import VASSAL.build.module.properties.MutablePropertiesContainer;
import VASSAL.build.module.properties.MutableProperty;
import VASSAL.command.Command;
import VASSAL.tools.DataArchive;
import VASSAL.tools.RecursionLimitException;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.swing.*;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SpecialDiceButtonTest {

  @Test
  public void defaultConstruct() throws RecursionLimitException, IOException {
    try (MockedStatic<GameModule> staticGm = Mockito.mockStatic(GameModule.class)) {
      final GameModule gm = mock(GameModule.class);
      staticGm.when(GameModule::getGameModule).thenReturn(gm);
      DataArchive da = new DataArchive("zipName");
      staticGm.when(gm::getDataArchive).thenReturn(da);
      GameState gameState = new GameState();
      staticGm.when(gm::getGameState).thenReturn(gameState);
      JToolBar toolbar = new JToolBar();
      staticGm.when(gm::getToolBar).thenReturn(toolbar);
      java.util.Random ran = new Random();
      staticGm.when(gm::getRNG).thenReturn(ran);

      // Capture the argument for the sendAndLog call after the dice roll.
      ArgumentCaptor<Command> sendAndLogCaptor = ArgumentCaptor.forClass(Command.class);

      SpecialDiceButton button = new SpecialDiceButton();
      SpecialDie die = new SpecialDie();
      SpecialDieFace f = new SpecialDieFace();
      f.setAttribute(SpecialDieFace.NUMERICAL_VALUE, 1);
      f.setAttribute(SpecialDieFace.ICON, "");
      SpecialDieFace f2 = new SpecialDieFace();
      f2.setAttribute(SpecialDieFace.NUMERICAL_VALUE, 2);
      f2.setAttribute(SpecialDieFace.ICON, "");
      die.addFace(f);
      die.addFace(f2);

      button.addSpecialDie(die);
      button.addTo(gm);

      button.DR();
      // Capture the sendAndLog parameter.
      verify(gm).sendAndLog(sendAndLogCaptor.capture());
      Command test = sendAndLogCaptor.getValue();
      test.getUndoCommand().execute();
      assertEquals(1, button.dice.size());
      //assertEquals(null, test);
    }
  }
}
