package git.artdeell.mojo.modding.modmanager;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

import me.andreasmelone.basicmodinfoparser.modfile.ModFile;

public class ContainedModInfo extends ModInfo {
    public ModFile modFile;
    @Nullable private final String name;

    public ContainedModInfo(File jarFile, ModFile modFile, @Nullable String name, String extraSearchTerms) {
        super(jarFile, extraSearchTerms);
        this.modFile = modFile;
        this.name = name;
    }

    @Override
    public String getName() {
        return name != null ? name : super.getName();
    }

    @Override
    protected void onFileMoved(File newFile) throws IOException {
        modFile = ModFile.create(newFile);
    }
}
