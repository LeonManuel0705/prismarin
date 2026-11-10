package net.prismarin.lobby.action;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SocialMenuActionTest {

    @Test
    void customItemsCanOpenTheSocialMenus() {
        assertEquals(new ItemAction(ItemAction.Type.MENU, ItemAction.MENU_FRIENDS), ItemAction.parse("menu: friends"));
        assertEquals(new ItemAction(ItemAction.Type.MENU, ItemAction.MENU_FRIENDS), ItemAction.parse("menu: Friend"));
        assertEquals(new ItemAction(ItemAction.Type.MENU, ItemAction.MENU_PARTY), ItemAction.parse("menu: party"));
        assertEquals(new ItemAction(ItemAction.Type.MENU, ItemAction.MENU_NICK), ItemAction.parse("menu: nick-tool"));
    }
}
