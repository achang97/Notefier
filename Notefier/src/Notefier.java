import java.awt.GridLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;

import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.sound.sampled.Mixer;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;

import javax.swing.JTextArea;
import java.awt.Color;

import javax.sound.sampled.AudioSystem;
import javax.swing.JLabel;

public class Notefier implements PitchDetectionHandler{

	public static final float A_CONST = (float) Math.pow(2, 1 / 12.0);
	public static final int MIDDLE_A_FREQ = 440;
	public static final int NUM_HALF_STEPS = 12;
	public static final String[] NOTE_TABLE = {"A", "A♯ / B♭", "B", "C", "C♯ / D♭",
			"D", "D♯ / E♭", "E", "F", "F♯ / G♭", "G", "G♯ / A♭"};
	public static final String MIXER_INFO_ID = "Default Audio Device, version Unknown Version";
	public static final float MIN_PROBABILITY = (float) 0.92;

	public static final int MIN_DB_VALUE = -70;
	public static final int MIN_DB_CHANGE = 10;

	private JFrame frame;
	private JTextArea textArea;
	private JTextArea noteArea;
	private JTextArea transcribeArea;
	private AudioDispatcher dispatcher;
	private Mixer mixer;

	private PitchEstimationAlgorithm algo;

	private String prevNote = "REST";
	private float prevNoteStart = 0;
	private String noiseCheck = "REST";
	private float prevDB = -100;
	private JTextField tempoField;

	private ArrayList<String> allNotes = new ArrayList<String>();
	private ArrayList<Float> allNoteLengths = new ArrayList<Float>();
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					Notefier window = new Notefier();
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public Notefier() {	
		//create the GUI
		initialize();
	}

	private boolean isInteger(String input) {
	    try {
	        Integer.parseInt(input);
	        return true;
	    }
	    catch(NumberFormatException e ) {
	        return false;
	    }
	}

	private void transcribe() {
		// TODO Auto-generated method stub
		String tempo = tempoField.getText();
		if (isInteger(tempo)) {
			int tempoVal = Integer.parseInt(tempo);
			
			// for now, assuming 4/4 time, so there are 4 beats in a bar
			float lengthOfBar = (float) (60 / (tempoVal / 4.0)); // length in seconds of one bar
			
			// create an array of note length values (whole, half, quarter, eighth, sixteenth)
			float[] noteLengthArray = new float[5];
			for (int i = 0; i < noteLengthArray.length; i++) {
				int divisor = 1 << i;
				noteLengthArray[i] = (float) (lengthOfBar / divisor);
			}
			
			for (int i = 0; i < allNotes.size(); i++) {
				String curNote = allNotes.get(i);
				float curNoteLength = allNoteLengths.get(i);
				float noteRatio = curNoteLength / lengthOfBar;
				System.out.println(curNote + ": " + noteRatio);
			}
		}
	}
	
	
	private void setMixer() throws LineUnavailableException {
		algo = PitchEstimationAlgorithm.YIN;
		//Search for correct mixer info
		for(Mixer.Info info : AudioSystem.getMixerInfo()){
			if (info.toString().equals(MIXER_INFO_ID)) {
				mixer = AudioSystem.getMixer(info);
			}
		}
		if(dispatcher!= null){
			dispatcher.stop();
		}

		float sampleRate = 44100;
		int bufferSize = 1024;
		int overlap = 0;

		textArea.append("Started listening with " + mixer.toString() + "\n");

		final AudioFormat format = new AudioFormat(sampleRate, 16, 1, true,
				true);
		final DataLine.Info dataLineInfo = new DataLine.Info(
				TargetDataLine.class, format);
		TargetDataLine line = null;
		line = (TargetDataLine) mixer.getLine(dataLineInfo);
		final int numberOfSamples = bufferSize;
		line.open(format, numberOfSamples);
		line.start();
		final AudioInputStream stream = new AudioInputStream(line);

		JVMAudioInputStream audioStream = new JVMAudioInputStream(stream);
		// create a new dispatcher
		dispatcher = new AudioDispatcher(audioStream, bufferSize,
				overlap);

		// add a processor
		dispatcher.addAudioProcessor(new PitchProcessor(algo, sampleRate, bufferSize, this));

		new Thread(dispatcher,"Audio dispatching").start();
	}

	public String getNote(float pitch) {
		float halfStepsFromA = (float) (Math.log(pitch / MIDDLE_A_FREQ) / Math.log(A_CONST)); // actual number of half steps from mid A

		int stepBelow = (int) Math.floor(halfStepsFromA); // checks the step above and below
		int stepAbove = (int) Math.ceil(halfStepsFromA);

		float deltaBelow = (float) Math.abs(halfStepsFromA - stepBelow); // distance from steps
		float deltaAbove = (float) Math.abs(stepAbove - halfStepsFromA);

		int numSteps = deltaBelow > deltaAbove ? stepAbove : stepBelow; // sets numSteps to closer calculated step

		int numStepsC4 = numSteps + 9; // number of half steps of note from C4
		int octave = Math.floorDiv(numStepsC4, NUM_HALF_STEPS) + 4; // get the value for octave

		numSteps = numSteps % NUM_HALF_STEPS; // gets index in NOTE_TABLE and returns appropriate string
		if (numSteps < 0) numSteps = NUM_HALF_STEPS + numSteps;

		return NOTE_TABLE[numSteps] + octave;
	}

	@Override
	public void handlePitch(PitchDetectionResult pitchDetectionResult,AudioEvent audioEvent) {
		float dbValue = (float) audioEvent.getdBSPL();
		float pitch = pitchDetectionResult.getPitch();

		if (pitch != -1 && dbValue > MIN_DB_VALUE) {
			float probability = pitchDetectionResult.getProbability();
			if (probability < MIN_PROBABILITY) return;

			double timeStamp = audioEvent.getTimeStamp();
			String note = getNote(pitch);

			String message = String.format("Pitch detected at %.2fs: %.2fHz ( %.2f probability, %.2f dB) The note is: %s\n", timeStamp,pitch,probability, dbValue, note);
			textArea.append(message);
			textArea.setCaretPosition(textArea.getDocument().getLength());

			prevDB = dbValue; // resets prevDB

			if (!note.equals(prevNote) || Math.abs(prevDB - dbValue) > MIN_DB_CHANGE) { // changed notes

				if (!noiseCheck.equals(note)) {
					noiseCheck = note;
					return;
				}

				float noteLength = (float) (timeStamp - prevNoteStart); 
				String newNoteMessage = String.format("Pitch detected: %s, Length: %f\n", prevNote, noteLength);
				noteArea.append(newNoteMessage);
				
				allNotes.add(prevNote);
				allNoteLengths.add(noteLength);

				prevNote = note;
				prevNoteStart = (float) timeStamp;
			}
		}
		/*
		else {
			String message = String.format("REST detected at %.2f dB\n", dbValue);
			textArea.append(message);
			textArea.setCaretPosition(textArea.getDocument().getLength());

			prevDB = dbValue; // resets prevDB

			if (!prevNote.equals("REST")) {
				if (!noiseCheck.equals("REST")) {
					noiseCheck = "REST";
					return;
				}

				double timeStamp = audioEvent.getTimeStamp();
				float noteLength = (float) (timeStamp - prevNoteStart); 
				String newNoteMessage = String.format("Pitch detected: %s, Length: %f\n", prevNote, noteLength);
				noteArea.append(newNoteMessage);

				allNotes.add(prevNote);
				allNoteLengths.add(noteLength);
				
				prevNote = "REST"; // indicates rest
				prevNoteStart = (float) timeStamp;
			}
		}
		*/
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frame = new JFrame();
		frame.getContentPane().setBackground(new Color(0, 100, 0));
		frame.setBounds(100, 100, 646, 521);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setLayout(null);

		JButton btnStartNoteifying = new JButton("Start");
		btnStartNoteifying.setBackground(new Color(255, 255, 255));
		btnStartNoteifying.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				try {
					setMixer();
				} catch (LineUnavailableException e1) {
					e1.printStackTrace();
				}
			}
		});
		btnStartNoteifying.setBounds(35, 20, 117, 29);
		frame.getContentPane().add(btnStartNoteifying);

		JScrollPane scrollPane = new JScrollPane();
		scrollPane.setBounds(35, 383, 586, 93);
		frame.getContentPane().add(scrollPane);

		textArea = new JTextArea();
		scrollPane.setRowHeaderView(textArea);
		textArea.setEditable(false);
		textArea.setBackground(new Color(255, 255, 255));

		JButton btnStop = new JButton("Stop");
		btnStop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (dispatcher != null) {
					dispatcher.stop();
				}
			}
		});
		btnStop.setBounds(35, 48, 117, 29);
		frame.getContentPane().add(btnStop);

		JScrollPane scrollPane_1 = new JScrollPane();
		scrollPane_1.setBounds(35, 278, 586, 93);
		frame.getContentPane().add(scrollPane_1);

		noteArea = new JTextArea();
		scrollPane_1.setColumnHeaderView(noteArea);
		noteArea.setEditable(false);
		
		JButton button = new JButton("Transcribe");
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				transcribe();
			}
		});
		button.setBackground(Color.WHITE);
		button.setBounds(506, 48, 117, 29);
		frame.getContentPane().add(button);
		
		JScrollPane scrollPane_2 = new JScrollPane();
		scrollPane_2.setBounds(35, 89, 586, 177);
		frame.getContentPane().add(scrollPane_2);
		
		transcribeArea = new JTextArea();
		scrollPane_2.setColumnHeaderView(transcribeArea);
		transcribeArea.setEditable(false);
		
		tempoField = new JTextField();
		tempoField.setBounds(443, 47, 62, 28);
		frame.getContentPane().add(tempoField);
		tempoField.setColumns(10);
		
		JLabel lblTempo = new JLabel("Tempo:");
		lblTempo.setBounds(389, 53, 61, 16);
		frame.getContentPane().add(lblTempo);
	}
}
