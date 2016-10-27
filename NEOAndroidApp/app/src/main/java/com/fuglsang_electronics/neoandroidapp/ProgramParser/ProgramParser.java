package com.fuglsang_electronics.neoandroidapp.ProgramParser;

import android.util.Log;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProgramParser {
    static final String TAG = "NEO_ProgramParser";
    static final int metaDataSize = 24;

    public byte[] ProgramName = new byte[8];
    public byte[] WordCount = new byte[2];

    byte[] loadedBytes;

    List<ProgramStep> steps = new ArrayList<>();

    public ProgramParser(String path)
    {
        try {
            loadedBytes = FileUtils.readFileToByteArray(new File(path));
        } catch (IOException ex) {
            Log.w(TAG, ex.toString());
        }

        parseProgramName();
        parseProgramSteps();
        parseWordCount();
    }

    private void parseProgramName()
    {
        for (int i = 0; i < ProgramName.length; i++)
        {
            ProgramName[i] = loadedBytes[1 + i];
        }
    }

    private void parseProgramSteps()
    {
        for (int i = metaDataSize; i < loadedBytes.length; i += ProgramStep.stepSizeInBytes)
        {
            byte[] bytes = new byte[ProgramStep.stepSizeInBytes];

            for (int k = 0; k < ProgramStep.stepSizeInBytes; k++)
            {
                bytes[k] = loadedBytes[i + k];
            }

            ProgramStep ps = ProgramStep.createProgramStep(bytes);

            if (ps != null)
            {
                steps.add(ps);
            }
            else
            {
                break;
            }
        }
    }

    private void parseWordCount()
    {
        int count = 0;

        for (int i = 0; i < steps.size(); i++)
        {
            count += steps.get(i).calcFinalWordCount();
        }

        this.WordCount[0] = (byte)((count & 0xFF00) >> 8);
        this.WordCount[1] = (byte)(count & 0xFF);
    }

    public byte[] getConverterdProgram()
    {
        int count = this.WordCount[0] << 8;
        count = count | (this.WordCount[1] & 0x000000FF);

        byte[] bytes = new byte[count * 2];
        int itr = 0;

        for (int i = 0; i < steps.size(); i++)
        {
            byte[][] words = steps.get(i).convert();

            for (int k = 0; k < words.length; k++)
            {
                bytes[itr++] = words[k][0];
                bytes[itr++] = words[k][1];
            }
        }

        return bytes;
    }
}
