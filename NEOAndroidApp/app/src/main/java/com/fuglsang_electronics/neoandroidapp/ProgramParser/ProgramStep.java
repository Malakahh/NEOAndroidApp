package com.fuglsang_electronics.neoandroidapp.ProgramParser;

public class ProgramStep {
    //Weird data encoding
    public enum ProgramStepBytes {
        Voltage_ByteLow(0),
        Voltage_ByteHigh(1),
        Current_ByteLow(2),
        Current_ByteHigh(3),
        VoltageLowJumpStep(4),
        VoltageHighJumpStep(5),
        VoltageLowJump_ByteLow(6),
        VoltageLowJump_ByteHigh(7),
        VoltageHighJump_ByteLow(8),
        VoltageHighJump_ByteHigh(9),
        CurrentLowJumpStep(10),
        CurrentHighJumpStep(11),
        CurrentLowJump_ByteLow(12),
        CurrentLowJump_ByteHigh(13),
        CurrentHighJump_ByteLow(14),
        CurrentHighJump_ByteHigh(15),
        RelativeTimeJumpStep(16),
        AbsoluteTimeJumpStep(17),
        RelativeTime_0(20),
        RelativeTime_1(21),
        RelativeTime_2(22),
        AbsoluteTime_0(24),
        AbsoluteTime_1(25),
        AbsoluteTime_2(26),
        C(28), //LED Charge
        F(29), //Flash LEDs
        E(30), //LED Error
        T(31), //Timer
        S(32);  //Output switch

        public int index;

        ProgramStepBytes(int index) {
            this.index = index;
        }
    }

    public static final int stepSizeInBytes = 36;

    private byte[] bytes;

    public ProgramStep(byte[] bytes)
    {
        this.bytes = bytes;
    }

    private boolean isSet(ProgramStepBytes psb)
    {
        return this.bytes[psb.index] != (byte)0xFF;
    }

    public int calcFinalWordCount()
    {
        int cntWords = 2; // We have a defined minimum if two WORDS
        ProgramStepBytes[] toTest = new ProgramStepBytes[]
                {
                        ProgramStepBytes.CurrentLowJumpStep,
                        ProgramStepBytes.CurrentHighJumpStep,
                        ProgramStepBytes.VoltageLowJumpStep,
                        ProgramStepBytes.VoltageHighJumpStep,
                        ProgramStepBytes.RelativeTimeJumpStep,
                        ProgramStepBytes.AbsoluteTimeJumpStep
                };

        for (int i = 0; i < toTest.length; i++)
        {
            if (isSet(toTest[i]))
            {
                cntWords++;

                //These require two words, per documnetation
                if (toTest[i] == ProgramStepBytes.RelativeTimeJumpStep || toTest[i] == ProgramStepBytes.AbsoluteTimeJumpStep)
                {
                    cntWords++;
                }
            }
        }

        return cntWords;
    }

    private byte[] generateWord_VoltageSetPoint()
    {
        byte[] word = new byte[2];

        word[0] = (byte)(this.bytes[ProgramStepBytes.Voltage_ByteHigh.index] & (byte)0x03);
        word[0] = (byte)( word[0] | (boolToBit(isSet(ProgramStepBytes.CurrentLowJumpStep)) << 2));
        word[0] = (byte)( word[0] | (boolToBit(isSet(ProgramStepBytes.CurrentHighJumpStep)) << 3));
        word[0] = (byte)( word[0] | (boolToBit(isSet(ProgramStepBytes.VoltageLowJumpStep)) << 4));
        word[0] = (byte)( word[0] | (boolToBit(isSet(ProgramStepBytes.VoltageHighJumpStep)) << 5));
        word[0] = (byte)( word[0] | (boolToBit(isSet(ProgramStepBytes.RelativeTimeJumpStep)) << 6));
        word[0] = (byte)( word[0] | (boolToBit(isSet(ProgramStepBytes.AbsoluteTimeJumpStep)) << 7));

        word[1] = this.bytes[ProgramStepBytes.Voltage_ByteLow.index];

        return word;
    }

    private byte[] generateWord_CurrentSetPoint()
    {
        byte[] word = new byte[2];

        word[0] = (byte)(this.bytes[ProgramStepBytes.Current_ByteHigh.index] & (byte)0x03);
        word[0] = (byte)( word[0] | (boolToBit(isSet(ProgramStepBytes.C)) << 2));
        word[0] = (byte)( word[0] | (boolToBit(isSet(ProgramStepBytes.F)) << 3));
        word[0] = (byte)( word[0] | (boolToBit(isSet(ProgramStepBytes.E)) << 4));
        word[0] = (byte)( word[0] | (boolToBit(isSet(ProgramStepBytes.S)) << 5));
        word[0] = (byte)( word[0] | (boolToBit(isSet(ProgramStepBytes.T)) << 6));

        word[1] = this.bytes[ProgramStepBytes.Current_ByteLow.index];

        return word;
    }

    private byte[][] generateWords_TimeJump(ProgramStepBytes step, ProgramStepBytes b0, ProgramStepBytes b1, ProgramStepBytes b2)
    {
        byte[][] words = new byte[2][];
        words[0] = new byte[2];
        words[1] = new byte[2];

        words[0][0] = (byte)(this.bytes[step.index] << 2); //Because... reason. Documentation states this needs to be the case
        words[0][1] = this.bytes[b0.index];

        words[1][0] = this.bytes[b2.index];
        words[1][1] = this.bytes[b1.index];

        return words;
    }

    private byte[][] generateWords_AbsoluteJump()
    {
        return generateWords_TimeJump(
                ProgramStepBytes.AbsoluteTimeJumpStep,
                ProgramStepBytes.AbsoluteTime_0,
                ProgramStepBytes.AbsoluteTime_1,
                ProgramStepBytes.AbsoluteTime_2);
    }

    private byte[][] generateWords_RelativeJump()
    {
        return generateWords_TimeJump(
                ProgramStepBytes.RelativeTimeJumpStep,
                ProgramStepBytes.RelativeTime_0,
                ProgramStepBytes.RelativeTime_1,
                ProgramStepBytes.RelativeTime_2);
    }

    private byte[] generateWord_BasicJump(ProgramStepBytes step, ProgramStepBytes high, ProgramStepBytes low)
    {
        byte[] word = new byte[2];

        word[0] = (byte)((this.bytes[step.index] & (byte)0x3F) << 2); //6 bit
        word[0] = (byte)(word[0] | (this.bytes[high.index] & (byte)0x03));

        word[1] = this.bytes[low.index];

        return word;
    }

    private byte[] generateWord_VoltageHighJump()
    {
        return generateWord_BasicJump(
                ProgramStepBytes.VoltageHighJumpStep,
                ProgramStepBytes.VoltageHighJump_ByteHigh,
                ProgramStepBytes.VoltageHighJump_ByteLow);
    }

    private byte[] generateWord_VoltageLowJump()
    {
        return generateWord_BasicJump(
                ProgramStepBytes.VoltageLowJumpStep,
                ProgramStepBytes.VoltageLowJump_ByteHigh,
                ProgramStepBytes.VoltageLowJump_ByteLow);
    }

    private byte[] generateWord_CurrentHighJump()
    {
        return generateWord_BasicJump(
                ProgramStepBytes.CurrentHighJumpStep,
                ProgramStepBytes.CurrentHighJump_ByteHigh,
                ProgramStepBytes.CurrentHighJump_ByteLow);
    }

    private byte[] generateWord_CurrentLowJump()
    {
        return generateWord_BasicJump(
                ProgramStepBytes.CurrentLowJumpStep,
                ProgramStepBytes.CurrentLowJump_ByteHigh,
                ProgramStepBytes.CurrentLowJump_ByteLow);
    }

    public byte[][] convert()
    {
        byte[][] words = new byte[calcFinalWordCount()][];
        int itr = 0;

        words[itr++] = generateWord_VoltageSetPoint();
        words[itr++] = generateWord_CurrentSetPoint();

        if (isSet(ProgramStepBytes.AbsoluteTimeJumpStep))
        {
            byte[][] temp = generateWords_AbsoluteJump();
            words[itr++] = temp[0];
            words[itr++] = temp[1];
        }

        if (isSet(ProgramStepBytes.RelativeTimeJumpStep))
        {
            byte[][] temp = generateWords_RelativeJump();
            words[itr++] = temp[0];
            words[itr++] = temp[1];
        }

        if (isSet(ProgramStepBytes.VoltageHighJumpStep))
        {
            words[itr++] = generateWord_VoltageHighJump();
        }

        if (isSet(ProgramStepBytes.VoltageLowJumpStep))
        {
            words[itr++] = generateWord_VoltageLowJump();
        }

        if (isSet(ProgramStepBytes.CurrentHighJumpStep))
        {
            words[itr++] = generateWord_CurrentHighJump();
        }

        if (isSet(ProgramStepBytes.CurrentLowJumpStep))
        {
            words[itr++] = generateWord_CurrentLowJump();
        }

        return words;
    }

    @Override
    public String toString()
    {
        String s = "";

        for(int i = 0; i < ProgramStepBytes.values().length; i++)
        {
            if (s != "")
            {
                s += "\t";
            }

            s += ProgramStepBytes.values()[i].toString() + "\t" + String.format("%02x", (bytes[ProgramStepBytes.values()[i].index]));
        }

        return s;
    }

    public static ProgramStep createProgramStep(byte[] bytes)
    {
        if (bytes[ProgramStepBytes.Voltage_ByteLow.index] == (byte)0xFF || bytes[ProgramStepBytes.Current_ByteLow.index] == (byte)0xFF)
            return null;

        return new ProgramStep(bytes);
    }

    private static int boolToBit(boolean b)
    {
        return b ? 1 : 0;
    }
}
