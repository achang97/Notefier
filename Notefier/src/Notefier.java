import java.awt.GridLayout;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.InvocationTargetException;

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

public class Notefier implements PitchDetectionHandler {

	public static final float A_CONST = (float) Math.pow(2, 1 / 12.0);
	public static final int MIDDLE_A_FREQ = 440;
	public static final int NUM_HALF_STEPS = 12;
	public static final String[] NOTE_TABLE = {"A", "A♯ / B♭", "B", "C", "C♯ / D♭",
			"D", "D♯ / E♭", "E", "F", "F♯ / G♭", "G", "G♯ / A♭"};
	public static final String MIXER_INFO_ID = "Default Audio Device, version Unknown Version";
	public static final float MIN_PROBABILITY = (float) 0.92;

	public static final int MAX_COUNT = 5;

	private JFrame frame;
	private JTextArea textArea;
	private JTextArea noteArea;
	private AudioDispatcher dispatcher;
	private Mixer mixer;

	private PitchEstimationAlgorithm algo;

	private String prevNote = "";
	private float prevNoteStart = 0;
	private String[] pastNoteArray = new String[MAX_COUNT];
	private int noteCount = 0;

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
			noteCount = 0;
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

		int octave = (numSteps / NUM_HALF_STEPS) + 4; // get the value for octave

		numSteps = numSteps % NUM_HALF_STEPS; // gets index in NOTE_TABLE and returns appropriate string
		if (numSteps < 0) numSteps = NUM_HALF_STEPS + numSteps;

		return NOTE_TABLE[numSteps] + octave;
	}

	private void addToArray(String note) {
		if (noteCount < MAX_COUNT) {
			pastNoteArray[noteCount++] = note;
		} else {
			System.arraycopy(pastNoteArray, 1, pastNoteArray, 0, MAX_COUNT - 1);
			pastNoteArray[MAX_COUNT - 1] = note;
		}
	}
	
	private boolean isValidChange() {
		String mostRecentNote = pastNoteArray[MAX_COUNT - 1];
		for (int i = 0; i < MAX_COUNT - 1; i++) {
			if (!pastNoteArray[i].equals(mostRecentNote)) return false;
		}
		return true;
	}
	
	@Override
	public void handlePitch(PitchDetectionResult pitchDetectionResult,AudioEvent audioEvent) {
		if (pitchDetectionResult.getPitch() != -1) {
			float pitch = pitchDetectionResult.getPitch();
			double timeStamp = audioEvent.getTimeStamp();
			float probability = pitchDetectionResult.getProbability();
			double rms = audioEvent.getRMS() * 100;
			String note = getNote(pitch);
			
			addToArray(note);
			if (pastNoteArray.length < MAX_COUNT) return;
			
			if (probability < MIN_PROBABILITY) return;
			
			String message = String.format("Pitch detected at %.2fs: %.2fHz ( %.2f probability, RMS: %.5f ) The note is: %s\n", timeStamp,pitch,probability,rms, note);
			textArea.append(message);
			textArea.setCaretPosition(textArea.getDocument().getLength());

			if (!note.equals(prevNote)) { // changed notes
				if (note.equals("F6") || note.equals("C6")) for (int i = 0; i < MAX_COUNT; i++) System.out.println(i + ": " + pastNoteArray[i]);
				if (!isValidChange()) return;
				if (!prevNote.equals("")) {
					float noteLength = (float) (timeStamp - prevNoteStart); 
					String newNoteMessage = String.format("Pitch detected: %s, Length: %f\n", prevNote, noteLength);
					noteArea.append(newNoteMessage);
				}
				prevNote = note;
				prevNoteStart = (float) timeStamp;
			}
		} else {
			addToArray("");
			if (pastNoteArray.length < MAX_COUNT) return;
			if (!prevNote.equals("")) { // changed notes
				if (!isValidChange()) return;
				double timeStamp = audioEvent.getTimeStamp();
				float noteLength = (float) (timeStamp - prevNoteStart); 
				String newNoteMessage = String.format("Pitch detected: %s, Length: %f\n", prevNote, noteLength);
				noteArea.append(newNoteMessage);

				prevNote = ""; // indicates rest
				prevNoteStart = (float) timeStamp;
			}
		}
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
		scrollPane.setBounds(35, 367, 586, 109);
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
		scrollPane_1.setBounds(35, 96, 586, 259);
		frame.getContentPane().add(scrollPane_1);

		noteArea = new JTextArea();
		scrollPane_1.setViewportView(noteArea);
		noteArea.setEditable(false);
	}
}
