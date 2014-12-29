package twitter4j;

import java.io.*;
import java.text.DateFormat;
import java.util.*;
import java.util.logging.*;

//import com.csvreader.*;

import java.util.Date;


public class LeeSer {

	public static void main(String[] args) throws IOException {
		/*Pruebas p=new Pruebas();
		int count=p.Numero();
		System.out.println(count);
	    try{
	      //use buffering
	      InputStream file = new FileInputStream("tweets.ser");
	      InputStream buffer = new BufferedInputStream(file);
	      ObjectInput input = new ObjectInputStream (buffer);
	      String outputFile = "Tweets.csv";
	      DateFormat df = DateFormat.getDateInstance(); 
          boolean alreadyExists = new File(outputFile).exists();
           
          if(alreadyExists){
              File ficheroUsuarios = new File(outputFile);
              ficheroUsuarios.delete();
          }        
	      try { 
	    	  CsvWriter csvOutput = new CsvWriter(new FileWriter(outputFile, true), ',');                 
	    	  csvOutput.write("Followers");
	    	  csvOutput.write("Fecha de Inscripción");
	    	  csvOutput.write("# de Favoritos");
	    	  csvOutput.write("Followings");
	    	  csvOutput.write("# de Tweets");
	    	  csvOutput.write("Nombre de Usuario");
	    	  csvOutput.write("Ubicación");
	    	  csvOutput.write("# de Personas que fav el tweet");
	    	  csvOutput.write("Latitud");
	    	  csvOutput.write("Longitud");
	    	  csvOutput.write("RT?");
	    	  csvOutput.write("fecha creación tweet");
	    	  csvOutput.write("Texto del Tweet");
	    	  csvOutput.endRecord();
	    	  for (int i=1;i<count;i++){
	    		  	Status obj = (Status) input.readObject(); 
	                   //datos de Usuario
	            	 User usuario = obj.getUser();
	                   //followers
	            	 csvOutput.write(Integer.toString(usuario.getFollowersCount()));
	                   //Cuando entró	                   
	            	 csvOutput.write(df.format(usuario.getCreatedAt()));
	                   // # de favoritos
	            	 csvOutput.write(Integer.toString(usuario.getFavouritesCount()));
	                   //followings
	            	 csvOutput.write(Integer.toString(usuario.getFriendsCount()));
	                   //num de tweets
	            	 csvOutput.write(Integer.toString(usuario.getStatusesCount()));
	                   //nombre de Usuario
	            	 csvOutput.write(usuario.getScreenName());
	                   //ubicación
	            	 csvOutput.write(usuario.getLocation());
	                   //datos del tweet
	            	   //Num de personas q marcaron el tweet como favorito
	            	 csvOutput.write(Integer.toString(obj.getFavoriteCount()));
	                   //ubicación en donde se escribió el tweet
	            	 if(obj.getGeoLocation()!=null){
	            	 csvOutput.write(Double.toString(obj.getGeoLocation().getLatitude()));
	            	 csvOutput.write(Double.toString(obj.getGeoLocation().getLongitude()));	
	            	}else{
	            	csvOutput.write("0");
	            	 csvOutput.write("0");
	            	}
	                  //Para saber si es un RT
	            	 csvOutput.write(Boolean.toString(obj.isRetweet()));
	            	 //Cuando crearon el tweet
	                 csvOutput.write(df.format(obj.getCreatedAt()));
	                   //texto del tweet
	                 csvOutput.write(obj.getText());
	                 csvOutput.endRecord();	
	            }
	    	  
	    	  csvOutput.close();
	        }catch (EOFException e) {  
	            // unfortunately ObjectInputStream doesn't have a good way to detect the end of the stream  
	            // so just ignore this exception - it's expected when there are no more objects  
	        	e.printStackTrace();
	        } catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {  
	            input.close();  
	        } 
	    }finally{}*/

	}

}
