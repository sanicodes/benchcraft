package git.artdeell.mojo.modding.modmanager;

import java.io.File;
import java.io.IOException;

import me.andreasmelone.basicmodinfoparser.modfile.ModFile;

public class ContainedModInfo extends ModInfo {
    public ModFile modFile;

    public ContainedModInfo(File jarFile, ModFile modFile, String extraSearchTerms) {
        super(jarFile, extraSearchTerms);
        this.modFile = modFile;
    }

    @Override
    protected void onFileMoved(File newFile) throws IOException {
        modFile = ModFile.create(newFile);
    }
}
