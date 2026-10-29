package com.zhibo.viewer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewerDisplayNamesTest {

    @Test
    void displayNickname_rules() {
        ViewerEntity withNick = new ViewerEntity();
        withNick.setId(1L);
        withNick.setNickname("阿坤");
        withNick.setPhone("13612348079");
        assertEquals("阿坤", ViewerDisplayNames.displayNickname(withNick));

        ViewerEntity phoneOnly = new ViewerEntity();
        phoneOnly.setId(2L);
        phoneOnly.setPhone("13612348079");
        assertEquals("用户8079", ViewerDisplayNames.displayNickname(phoneOnly));

        ViewerEntity neither = new ViewerEntity();
        neither.setId(3L);
        assertEquals("用户_3", ViewerDisplayNames.displayNickname(neither));

        assertEquals("用户_0", ViewerDisplayNames.displayNickname(null));
        assertEquals("用户_12", ViewerDisplayNames.displayNicknameForId(12L));
    }
}
