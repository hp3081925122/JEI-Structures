package org.hp.jei_structures.jei;

public interface EmiStructureScrollableWidget {

    boolean jeiStructures$mouseScrolled(int mouseX, int mouseY, double delta);

    boolean jeiStructures$mouseDragged(int mouseX, int mouseY, int button, double dragX, double dragY);

    boolean jeiStructures$mouseReleased(int mouseX, int mouseY, int button);
}
