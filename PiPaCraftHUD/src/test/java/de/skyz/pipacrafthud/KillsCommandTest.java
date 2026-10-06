package de.skyz.pipacrafthud;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.command.*;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.*;

/** Exercise the actual command's public permissions and its vanilla chat buttons. */
public final class KillsCommandTest {
    private static int tests;
    private static void eq(Object expected, Object actual) {
        tests++; if (!Objects.equals(expected, actual)) throw new AssertionError("expected=" + expected + ", actual=" + actual);
    }
    public static void main(String[] args) throws Exception {
        PiPaCraftHUD mod = new PiPaCraftHUD();
        HudConfig config = new HudConfig();
        Field field = PiPaCraftHUD.class.getDeclaredField("config"); field.setAccessible(true); field.set(mod, config);
        Class<?> type = Class.forName("de.skyz.pipacrafthud.PiPaCraftHUD$KillsCommand");
        Constructor<?> constructor = type.getDeclaredConstructor(PiPaCraftHUD.class); constructor.setAccessible(true);
        CommandBase command = (CommandBase)constructor.newInstance(mod);
        List<ITextComponent> messages = new ArrayList<>();
        ICommandSender nonOp = (ICommandSender)Proxy.newProxyInstance(ICommandSender.class.getClassLoader(), new Class<?>[]{ICommandSender.class},
                (proxy, method, values) -> {
                    if (method.getName().equals("sendMessage")) messages.add((ITextComponent)values[0]);
                    if (method.getReturnType() == boolean.class) return false; // Denies every OP permission.
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                });
        eq("kills", command.getName()); eq(Collections.singletonList("pipakills"), command.getAliases());
        eq(0, command.getRequiredPermissionLevel()); eq(false, nonOp.canUseCommand(0, "kills"));
        eq(true, command.checkPermission(null, nonOp));
        config.kills.enabled = false;
        command.execute(null, nonOp, new String[0]);
        eq(1, messages.size()); eq(HudText.color(config.kills.disabled), messages.get(0).getUnformattedText());
        config.kills.enabled = true; messages.clear();
        command.execute(null, nonOp, new String[]{"1", "extra"});
        eq(1, messages.size()); eq(HudText.color(config.kills.usage), messages.get(0).getUnformattedText());
        Method buttonMethod = type.getDeclaredMethod("button", String.class, int.class, String.class, Map.class);
        buttonMethod.setAccessible(true);
        ITextComponent button = (ITextComponent)buttonMethod.invoke(command, config.kills.next, 2, "/pipakills", Collections.emptyMap());
        eq(HudText.color(config.kills.next), button.getUnformattedText());
        eq(ClickEvent.Action.RUN_COMMAND, button.getStyle().getClickEvent().getAction());
        eq("/pipakills 2", button.getStyle().getClickEvent().getValue());
        eq(HoverEvent.Action.SHOW_TEXT, button.getStyle().getHoverEvent().getAction());
        eq(HudText.color("&fZu Seite &d2"), button.getStyle().getHoverEvent().getValue().getUnformattedText());
        System.out.println("PiPaCraft HUD: " + tests + " Befehls-/Berechtigungs-/Chatbutton-Pruefungen bestanden.");
    }
}
