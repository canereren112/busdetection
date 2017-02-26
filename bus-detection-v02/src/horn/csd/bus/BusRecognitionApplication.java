package horn.csd.bus;

import org.opencv.core.Core;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.fxml.FXMLLoader;

public class BusRecognitionApplication extends Application
{

	@Override
	public void start(Stage primaryStage)
	{
		try
		{
			BorderPane mainBorderPane = (BorderPane) FXMLLoader.load(getClass().getResource("BusRecognition.fxml"));
			mainBorderPane.setStyle("-fx-background-color: #dddddd;");
			Scene scene = new Scene(mainBorderPane, 1150, 700);
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());

			
			primaryStage.setTitle("Bus Recognition");
			primaryStage.setScene(scene);
			// show the GUI
			primaryStage.show();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args)
	{
		// load the native OpenCV library
		System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
		
		launch(args);
	}
}
