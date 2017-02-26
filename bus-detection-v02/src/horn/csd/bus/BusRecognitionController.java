package horn.csd.bus;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import horn.csd.core.Constants;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;


public class BusRecognitionController
{
	// FXML camera button
	@FXML
	private Button axisCameraButton;
	// the FXML area for showing the current frame
	@FXML
	private ImageView originalFrame;
	// the FXML area for showing the mask
	@FXML
	private ImageView maskImage;
	// the FXML area for showing the output of the morphological operations
	@FXML
	private ImageView morphImage;
	// FXML slider for setting HSV ranges
	@FXML
	private Slider hueBegin;
	@FXML
	private Slider hueEnd;
	@FXML
	private Slider saturationBegin;
	@FXML
	private Slider saturationEnd;
	@FXML
	private Slider valueBegin;
	@FXML
	private Slider valueEnd;
	// FXML label to show the current values set with the sliders
	@FXML
	private Label hsvCurrentValues;
	@FXML
	private Label isBusDetected;
	
	// a timer for acquiring the video stream
	private ScheduledExecutorService timer;
	// the OpenCV object that performs the video capture
	private VideoCapture capture = new VideoCapture();
	// a flag to change the button behavior
	private boolean cameraActive;
	
	// property for object binding
	private ObjectProperty<String> hsvValuesProp;
	private ObjectProperty<String> busDetectedProp;
	
	private String serverName = Constants.AXIS_IP;
	private int port = Integer.parseInt(Constants.AXIS_PORT);
	private String resolution = Constants.AXIS_RESOLUTION;
	private String fps = Constants.AXIS_FPS;
	
	@FXML
	private void startAxisCamera()
	{
		// bind a text property with the string containing the current range of
		// HSV values for object detection
		hsvValuesProp = new SimpleObjectProperty<>();
		busDetectedProp = new SimpleObjectProperty<>();
		
		this.hsvCurrentValues.textProperty().bind(hsvValuesProp);
		this.isBusDetected.textProperty().bind(busDetectedProp);
		this.isBusDetected.setTextFill(javafx.scene.paint.Color.web("#028900"));
		this.isBusDetected.setFont(new Font("Arial", 30));

		// set a fixed width for all the image to show and preserve image ratio
		this.imageViewProperties(this.originalFrame, 650);
		this.imageViewProperties(this.maskImage, 300);
		this.imageViewProperties(this.morphImage, 300);
		
		if (!this.cameraActive)
		{
			// start the video capture
			this.capture.open(0);
			
			// is the video stream available?
			if (this.capture.isOpened())
			{
				this.cameraActive = true;
				
				// grab a frame every 33 ms (30 frames/sec)
				Runnable frameGrabber = new Runnable() {
					
					@Override
					public void run()
					{
						grabAxisFrame();
					}
				};
				
				this.timer = Executors.newSingleThreadScheduledExecutor();
				this.timer.scheduleAtFixedRate(frameGrabber, 0,Integer.valueOf( Constants.AXIS_FPS), TimeUnit.MILLISECONDS);
				
				// update the button content
				this.axisCameraButton.setText("Stop Axis Camera");
			}
			else
			{
				// log the error
				System.err.println("Failed to open the camera connection...");
			}
		}
		else
		{
			// the camera is not active at this point
			this.cameraActive = false;
			// update again the button content
			this.axisCameraButton.setText("Start Axis Camera");
			
			// stop the timer
			try
			{
				this.timer.shutdown();
				this.timer.awaitTermination(33, TimeUnit.MILLISECONDS);
			}
			catch (InterruptedException e)
			{
				// log the exception
				System.err.println("Exception in stopping the Axis frame capture, trying to release the Axis camera now... " + e);
			}
			
			// release the camera
			this.capture.release();
		}
	}

		
	private void grabAxisFrame()
	{
		// init everything
		Image imageToShow = null;
		Mat frame = new Mat();
		
		try {
			fillAxisCameraInformation();
			System.out.println("Connecting to " + serverName + " on port " + port);
			Socket client = new Socket(serverName, port);
			System.out.println("Just connected to " + client.getRemoteSocketAddress());

			try {
				InputStream is1 = client.getInputStream();
				InputStreamReader isr1 = new InputStreamReader(is1);
				BufferedReader br1 = new BufferedReader(isr1);
				String message1 = br1.readLine();
				//System.out.println("Available resolution: " + message1);
			} catch(Exception e) {
				e.printStackTrace();
			}

			OutputStream os = client.getOutputStream();			
			
	

			OutputStreamWriter osw1 = new OutputStreamWriter(os);
			BufferedWriter bw1 = new BufferedWriter(osw1);
			String send = "resolution=" + resolution + "&" + "fps=" + fps;
			
			
			bw1.write(send);
			bw1.flush();

			System.out.println("Sent " + send + " to server.");
			int size;
			int i, j = 0;
			
			try {
				InputStream in = client.getInputStream();
				DataInputStream data = new DataInputStream(in);

				while (true) {
					size = data.readInt();
					System.out.println("Frame size: " + size);

					byte[] bytes = new byte[size]; 
					for(i = 0; i < size; i++) {
						in.read(bytes, i, 1);
					}

					System.out.println("Refreshing the Image");
					InputStream tempInputStream = new ByteArrayInputStream(bytes);
					BufferedImage image = ImageIO.read(tempInputStream);
					
					frame= bufferedImageToMat(image);
					if (!frame.empty()){
						Mat blurredImage = new Mat();
						Mat hsvImage = new Mat();
						Mat mask = new Mat();
						Mat morphOutput = new Mat();
						
						// remove some noise
						Imgproc.blur(frame, blurredImage, new Size(7, 7));
						
						// convert the frame to HSV
						Imgproc.cvtColor(blurredImage, hsvImage, Imgproc.COLOR_BGR2HSV);
						
						// get thresholding values from the UI
						// remember: H ranges 0-180, S and V range 0-255
						Scalar minValues = new Scalar(this.hueBegin.getValue(), this.saturationBegin.getValue(), this.valueBegin.getValue());
						Scalar maxValues = new Scalar(this.hueEnd.getValue(), this.saturationEnd.getValue(), this.valueEnd.getValue());
				
						
						// show the current selected HSV range
						String valuesToPrint = "Hue range: " + minValues.val[0] + "-" + maxValues.val[0]
								+ "\tSaturation range: " + minValues.val[1] + "-" + maxValues.val[1] + "\tValue range: "
								+ minValues.val[2] + "-" + maxValues.val[2];
						this.onFXThread(this.hsvValuesProp, valuesToPrint);
						
						Core.inRange(hsvImage, minValues, maxValues, mask);
						// show the partial output
						this.onFXThread(this.maskImage.imageProperty(), this.mat2Image(mask));//Top Small Screen
						
						// morphological operators
						// dilate with large element, erode with small ones
						Mat dilateElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(24, 24));
						Mat erodeElement = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(12, 12));
						
						Imgproc.erode(mask, morphOutput, erodeElement);
						Imgproc.erode(mask, morphOutput, erodeElement);
						
						Imgproc.dilate(mask, morphOutput, dilateElement);
						Imgproc.dilate(mask, morphOutput, dilateElement);
						
						this.onFXThread(this.busDetectedProp, isBusDetectedResult(morphOutput));
						// show the partial output
						this.onFXThread(this.morphImage.imageProperty(), this.mat2Image(morphOutput));//Below Small Screen
						
						
						// convert the Mat object (OpenCV) to Image (JavaFX)
						imageToShow = mat2Image(frame);
						this.originalFrame.setImage(imageToShow);

					}
					
				}
			} catch(Exception e) {
				e.printStackTrace();
			}
			client.close();
		} catch (IOException e) {
				e.printStackTrace();
		}
		
		
		
	}

	private void imageViewProperties(ImageView image, int dimension)
	{
		// set a fixed width for the given ImageView
		image.setFitWidth(dimension);
		// preserve the image ratio
		image.setPreserveRatio(true);
	}
	

	private Image mat2Image(Mat frame)
	{
		// create a temporary buffer
		MatOfByte buffer = new MatOfByte();
		// encode the frame in the buffer, according to the PNG format
		Imgcodecs.imencode(".png", frame, buffer);
		// build and return an Image created from the image encoded in the
		// buffer
		return new Image(new ByteArrayInputStream(buffer.toArray()));
	}
	

	private <T> void onFXThread(final ObjectProperty<T> property, final T value)
	{
		Platform.runLater(new Runnable() {
			
			@Override
			public void run()
			{
				property.set(value);
			}
		});
	}
	
	
	
	private String isBusDetectedResult(Mat mat){
		if(countNumberOfPixels(mat)>15000){
			return "Bus detected";
		}else{
			return "There is no bus";
		}
	} 
	
	private int countNumberOfPixels(Mat mat){
		BufferedImage bufImage = matToBufferedImage(mat);
		int count=0;
  	   	for(int i=0; i< bufImage.getWidth(); i++){
  	   		for(int j=0; j < bufImage.getHeight(); j++){
  	   			if(bufImage.getRGB(i, j) == new Color(255, 255, 255).getRGB()){
  	  	   			count++;
  	   			}
  	   		}
  	   	}
  	   	return count;
	}
	
    private  BufferedImage matToBufferedImage(Mat mat) {

        if (mat.height() > 0 && mat.width() > 0) {
            BufferedImage image = new BufferedImage(mat.width(), mat.height(), BufferedImage.TYPE_3BYTE_BGR);
            WritableRaster raster = image.getRaster();
            DataBufferByte dataBuffer = (DataBufferByte) raster.getDataBuffer();
            byte[] data = dataBuffer.getData();
            mat.get(0, 0, data);
            return image;
        }

        return null;
    }
    
	public void fillAxisCameraInformation() {
		System.out.println("IP is " + Constants.AXIS_IP);
		System.out.println("Port is " + Constants.AXIS_PORT);
		System.out.println("Resolution is " + Constants.AXIS_RESOLUTION);
		System.out.println("FPS is" + Constants.AXIS_FPS);

		serverName = Constants.AXIS_IP;
		port = Integer.parseInt(Constants.AXIS_PORT);
		resolution = Constants.AXIS_RESOLUTION;
		fps = Constants.AXIS_FPS;

	}
	
	public static Mat bufferedImageToMat(BufferedImage bi) {
		  Mat mat = new Mat(bi.getHeight(), bi.getWidth(), CvType.CV_8UC3);
		  byte[] data = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
		  mat.put(0, 0, data);
		  return mat;
		}
}
