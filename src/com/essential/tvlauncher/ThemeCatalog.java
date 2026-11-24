package com.essential.tvlauncher;

public class ThemeCatalog {

    public static final int SCENE_SUNSET = 0;
    public static final int SCENE_MONKEY_ISLAND = 1;
    public static final int SCENE_CYBER_CITY = 2;
    public static final int SCENE_FOREST_NIGHT = 3;

    public static class ThemeSpec {
        public final String name;
        public final int sceneType;
        public final int[] skyBands;
        public final int[] sunColors;
        public final int mountainFar;
        public final int mountainNear;
        public final int waterBase;
        public final int waterHighlight;
        public final int accentColor;
        public final int starColor;

        public ThemeSpec(String name, int sceneType, int[] skyBands, int[] sunColors,
                         int mountainFar, int mountainNear,
                         int waterBase, int waterHighlight,
                         int accentColor, int starColor) {
            this.name = name;
            this.sceneType = sceneType;
            this.skyBands = skyBands;
            this.sunColors = sunColors;
            this.mountainFar = mountainFar;
            this.mountainNear = mountainNear;
            this.waterBase = waterBase;
            this.waterHighlight = waterHighlight;
            this.accentColor = accentColor;
            this.starColor = starColor;
        }
    }

    public static final ThemeSpec[] THEMES = new ThemeSpec[] {
            // 0: Classic 8-Bit Digital Sunset
            new ThemeSpec(
                    "8-Bit Digital Sunset",
                    SCENE_SUNSET,
                    new int[] { 0xFF140C2E, 0xFF2B1348, 0xFF5D1D5C, 0xFFA62F5A, 0xFFE35A4F, 0xFFF79B52 },
                    new int[] { 0xFFFFF08A, 0xFFFFC15E, 0xFFFF785A, 0xFFE83B72 },
                    0xFF28123D,
                    0xFF150A24,
                    0xFF120821,
                    0xFFFF885B,
                    0xFFFFC15E,
                    0xFFFFF5D6
            ),
            // 1: Monkey Island Night (LucasArts EGA/VGA Lookout Point)
            new ThemeSpec(
                    "Monkey Island Night",
                    SCENE_MONKEY_ISLAND,
                    new int[] { 0xFF060B24, 0xFF0A143B, 0xFF112257, 0xFF1A3575, 0xFF244B8E, 0xFF2F64A6 },
                    new int[] { 0xFFF8FAFC, 0xFFE2F1FF, 0xFFB8DCFF, 0xFF8ABAE8 },
                    0xFF101D40,
                    0xFF080F24,
                    0xFF07112B,
                    0xFF7CC4FA,
                    0xFFFFB74D,
                    0xFFE0F2FE
            ),
            // 2: Melee Island Sunset (Caribbean Twilight Lookout)
            new ThemeSpec(
                    "Melee Island Sunset",
                    SCENE_MONKEY_ISLAND,
                    new int[] { 0xFF120B2E, 0xFF2B144A, 0xFF5A1D5C, 0xFF9C2E59, 0xFFD9534F, 0xFFF2994A },
                    new int[] { 0xFFFFF3B0, 0xFFFFCA6B, 0xFFFF8559, 0xFFE6486E },
                    0xFF241138,
                    0xFF120820,
                    0xFF110A24,
                    0xFFFF9A62,
                    0xFFFFCA6B,
                    0xFFFFF5D6
            ),
            // 3: Neo Tokyo 1988 (Pixel Skyline & Mount Fuji)
            new ThemeSpec(
                    "Neo Tokyo 1988",
                    SCENE_CYBER_CITY,
                    new int[] { 0xFF09081C, 0xFF19123B, 0xFF3A1B59, 0xFF752669, 0xFFC23B68, 0xFFF27368 },
                    new int[] { 0xFFFFE599, 0xFFFF9E7D, 0xFFFF4D8D, 0xFFC724B1 },
                    0xFF1F1638,
                    0xFF0F0B1F,
                    0xFF0B0818,
                    0xFF38BDF8,
                    0xFF38BDF8,
                    0xFFE0F2FE
            ),
            // 4: Forest Fireflies (8-Bit Pine Woods at Midnight)
            new ThemeSpec(
                    "Forest Fireflies",
                    SCENE_FOREST_NIGHT,
                    new int[] { 0xFF040D12, 0xFF071A21, 0xFF0C2A32, 0xFF133B42, 0xFF1B4D4F, 0xFF27615C },
                    new int[] { 0xFFF1F5F9, 0xFFD9F99D, 0xFFBEF264, 0xFF86EFAC },
                    0xFF0A241E,
                    0xFF051410,
                    0xFF040E0C,
                    0xFF86EFAC,
                    0xFF4ADE80,
                    0xFFDCFCE7
            ),
            // 5: Synthwave Dusk
            new ThemeSpec(
                    "Synthwave Dusk",
                    SCENE_SUNSET,
                    new int[] { 0xFF0B0A24, 0xFF1C1245, 0xFF421863, 0xFF8B2273, 0xFFD93B7A, 0xFFFF7A85 },
                    new int[] { 0xFFFFE599, 0xFFFF9E7D, 0xFFFF4D8D, 0xFFC724B1 },
                    0xFF23103E,
                    0xFF110722,
                    0xFF0E061C,
                    0xFFFF5E99,
                    0xFFFF66B2,
                    0xFFE0F2FE
            ),
            // 6: Amber Arcade
            new ThemeSpec(
                    "Amber Arcade",
                    SCENE_SUNSET,
                    new int[] { 0xFF1A1020, 0xFF381A2C, 0xFF6B2834, 0xFFA84232, 0xFFDC6E35, 0xFFF7AC48 },
                    new int[] { 0xFFFFF7B2, 0xFFFFD166, 0xFFF48C37, 0xFFD6453A },
                    0xFF361924,
                    0xFF1C0C14,
                    0xFF160A10,
                    0xFFF7A243,
                    0xFFFFD166,
                    0xFFFFF3C4
            ),
            // 7: Pixel Lagoon
            new ThemeSpec(
                    "Pixel Lagoon",
                    SCENE_SUNSET,
                    new int[] { 0xFF071B26, 0xFF0E2F44, 0xFF1F4260, 0xFF4B4778, 0xFF9C4E78, 0xFFE67B73 },
                    new int[] { 0xFFFFF2B2, 0xFFFFB87A, 0xFFFF7B89, 0xFFD44D7A },
                    0xFF16253A,
                    0xFF0B1320,
                    0xFF081421,
                    0xFFFF8F85,
                    0xFF5EEAD4,
                    0xFFE0F7FA
            )
    };

    public static ThemeSpec get(int index) {
        if (index < 0 || index >= THEMES.length) {
            return THEMES[0];
        }
        return THEMES[index];
    }
}
