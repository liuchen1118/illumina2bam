/*
 * Copyright (C) 2011 GRL
 *
 * This library is free software. You can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package illumina;

import java.io.File;
import java.util.List;
import net.sf.picard.cmdline.Option;
import net.sf.picard.cmdline.StandardOptionDefinitions;
import net.sf.picard.cmdline.Usage;
import net.sf.picard.io.IoUtil;
import net.sf.picard.util.Log;
import net.sf.samtools.SAMFileHeader;
import net.sf.samtools.SAMFileReader;
import net.sf.samtools.SAMFileWriter;
import net.sf.samtools.SAMFileWriterFactory;
import net.sf.samtools.SAMProgramRecord;
import net.sf.samtools.SAMRecord;
import net.sf.samtools.SAMRecord.SAMTagAndValue;
import net.sf.samtools.SAMRecordIterator;
import net.sf.samtools.SAMRecordUtil;
import net.sf.samtools.SAMSequenceDictionary;


/**
 * A command-line tool to merge BAM/SAM alignment info in a bam
 * with the data in an unmapped BAM file,
 * producing a third BAM file that has alignment data and all the additional data from the unmapped BAM
 * 
 * Two bam files must have the same set of records and in the same order.
 * 
 * Only SQ records and alignment PG in the aligned bam file will be added to the output.
 * 
 * All header information in the second bam header will be kept, except SQ records.
 * 
 * @author Guoying Qi
 */

public class BamMerger extends Illumina2bamCommandLine {
    
    private final Log log = Log.getInstance(BamMerger.class);
    
    private final String programName = "bamMerger";
    
    private final String programDS = "A command-line tool to merge BAM/SAM alignment info in the first input fie"
            + " with the data in an unmapped BAM file,"
            + " producing a third BAM file that has alignment data"
            + " and all the additional datafrom the unmapped BAM";
   
    @Usage(programVersion= version)
    public final String USAGE = this.getStandardUsagePreamble() + this.programDS + ". "; 
 
    @Option(shortName= "ALIGNED", doc="The input SAM or BAM file with alignment.")
    public File ALIGNED_BAM;
    
    @Option(shortName= "PG", doc="The alignment program ID in the header of the SAM or BAM file with alignment.")
    public String ALIGNMENT_PROGRAM_ID = "bwa";
    
    @Option(shortName= StandardOptionDefinitions.INPUT_SHORT_NAME, doc="The input SAM or BAM file to merge.")
    public File INPUT;

    @Option(shortName=StandardOptionDefinitions.OUTPUT_SHORT_NAME, doc="The output file after merging.")
    public File OUTPUT;

    @Override
    protected int doWork() {
      
        log.info("Checking input and output file");
        IoUtil.assertFileIsReadable(ALIGNED_BAM);
        IoUtil.assertFileIsReadable(INPUT);
        IoUtil.assertFileIsWritable(OUTPUT);
        
        log.info("Open aligned bam file " + ALIGNED_BAM.getName());
        final SAMFileReader alignments  = new SAMFileReader(ALIGNED_BAM);
        SAMFileHeader headerAlignments = alignments.getFileHeader();
        SAMSequenceDictionary sequenceDictionary = headerAlignments.getSequenceDictionary();
        SAMProgramRecord alignmentProgram = null;
        if(this.ALIGNMENT_PROGRAM_ID != null){
           alignmentProgram = headerAlignments.getProgramRecord(this.ALIGNMENT_PROGRAM_ID);
        }
        log.info("Open input file to merge " + INPUT.getName());
        final SAMFileReader in  = new SAMFileReader(INPUT);        
        final SAMFileHeader header = in.getFileHeader();
        
        final SAMFileHeader outputHeader = header.clone();
        outputHeader.setSequenceDictionary(sequenceDictionary);        
        if(alignmentProgram != null ){
           this.addProgramRecordToHead(outputHeader, alignmentProgram);
        }        
        this.addProgramRecordToHead(outputHeader, this.getThisProgramRecord(programName, programDS));
        
        log.info("Open output file with header: " + OUTPUT.getName());
        final SAMFileWriter out = new SAMFileWriterFactory().makeSAMOrBAMWriter(outputHeader,  true, OUTPUT);
        
        log.info("Starting to merge");

        SAMRecordIterator iterator = alignments.iterator();

        for(SAMRecord record: in){

            SAMRecord alignment = iterator.next();

            String readName1 = record.getReadName();
            String readName2 = alignment.getReadName();
            
            boolean pairedRead1 = record.getReadPairedFlag();
            boolean pairedRead2 = alignment.getReadPairedFlag();
            
            boolean firstOfPair1 = record.getFirstOfPairFlag();
            boolean firstOfPair2 = alignment.getFirstOfPairFlag();
  
            if( !readName1.equals(readName2)
                || pairedRead1 != pairedRead2
                || firstOfPair1 != firstOfPair2
            ){
                throw new RuntimeException("Two records not match: " + readName1 + " -- " + readName2);
                
            }
 
            this.mergeRecords(alignment, record);

            out.addAlignment(alignment);
        }
        
        out.close();

        log.info("Merging finished, merged file: " + this.OUTPUT);
        
        return 0;
    }
    
    /**
     * 
     * @param alignment
     * @param record 
     */    
    public void mergeRecords(SAMRecord alignment, SAMRecord record){
        
        boolean isNegativeStrand1 = alignment.getReadNegativeStrandFlag();
        boolean isNegativeStrand2 = record.getReadNegativeStrandFlag();
        if( isNegativeStrand1 != isNegativeStrand2 ){
            SAMRecordUtil.reverseComplement(record);
        }
        
        List<SAMTagAndValue> attributeList = record.getAttributes();
        for(SAMTagAndValue attribute : attributeList){
            
            String tag = attribute.tag;
            Object value = attribute.value;            
            if( alignment.getAttribute(tag) == null && value != null ){
               alignment.setAttribute(tag, value);
            }
        }

    }
    
    
    /**
     * example: ALIGNED=testdata/bam/6210_8_aligned.sam I=testdata/bam/6210_8.sam OUTPUT=testdata/6210_8_merged.bam  TMP_DIR=testdata VALIDATION_STRINGENCY=SILENT
     * 
     * @param args 
     */
    public static void main(final String[] args) {
        
        System.exit(new BamMerger().instanceMain(args));
    }
}