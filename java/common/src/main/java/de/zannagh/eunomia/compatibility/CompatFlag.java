package de.zannagh.eunomia.compatibility;

import de.zannagh.eunomia.common.SemanticVersion;

import javax.swing.*;
import java.util.List;

public interface CompatFlag {
    SemanticVersion since();

    List<String> classNames();

    boolean needsInitialization();

    List<CompatFlag> dependencies();
}
