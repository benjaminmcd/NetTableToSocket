package gov.nasa.HoloFirst;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.*;

import edu.wpi.first.wpilibj.tables.*;
import edu.wpi.first.wpilibj.networktables.*;

import javax.xml.parsers.*;
import org.w3c.dom.*;

import java.io.DataOutputStream;
import java.io.File;

/******************************************************************************
 *
 * This class connects to a NetworkTable that is used by the WPI SmartDashboard
 * to collect some data and send it to [a Hololens via] a UDP socket.
 * 
 * A configuration file is used to define what table is used, where the
 * messages get sent, and what values are in the message.
 * 
 *****************************************************************************/
public class NetTableToSocket 
{
	enum  ValueType
	{
		DOUBLE, FLOAT, INT, SHORT, BOOLEAN
	};
	
	class ValueItem
	{
		public ValueType type;
		public Object value;
	}
	
	private static final String TABLE_NAME = "SmartDashboard";
	private static final float MSG_PERIOD = 0.1f; // seconds
	private static final float CONNECT_PERIOD = 2.0f; // seconds
	private static final int MESSAGE_BUFFER_SIZE = 4096;
	private static final int MESSAGE_HEADER_SIZE = 6;
	
	private boolean done = false;
	private short msg_count = 0;

	private boolean use_tcp = false;
	
	private HashMap<String, Integer> value_map = new HashMap<String, Integer>();
	private Vector<ValueItem> value_list = new Vector<ValueItem>();
	
	private MyTableListener table_listener;

	private String dst_host = "localhost";
	private int dst_port = 4322;
	
	private String table_host = "";
	
	/**************************************************************************
	 * 
	 * The main method is used to create and configure an instance of this
	 * class.
	 * 
	 * @param args[0] - the name of the configuration file
	 * 
	 *************************************************************************/
	public static void main(String[] args)
	{
		if (args.length == 1)
		{
			NetTableToSocket app = new NetTableToSocket();
			app.loadConfig(args[0]);
			app.run();
		}
		else
		{
			System.out.println("USAGE: NetTableToSocket <config file>");
			return;
		}
	}

	/**************************************************************************
	 * 
	 * This constructor creates a single instance of a DashboardToUdp and
	 * does some initialization of  values used by all instances.
	 * 
	 *************************************************************************/
	public NetTableToSocket()
	{
		table_listener = new MyTableListener(this);
	}
	
	private void run()
	{
		System.out.println("Getting data from " + table_host + ":" + NetworkTable.DEFAULT_PORT);
		
		NetworkTable.setClientMode();
		NetworkTable.setIPAddress(table_host);
		NetworkTable.setPort(NetworkTable.DEFAULT_PORT);
		NetworkTable.setNetworkIdentity(TABLE_NAME);
		NetworkTable.initialize();

		ITable table = NetworkTable.getTable(TABLE_NAME);
		
        table.addTableListenerEx(table_listener, ITable.NOTIFY_IMMEDIATE | ITable.NOTIFY_LOCAL | ITable.NOTIFY_NEW | ITable.NOTIFY_UPDATE);
        table.addSubTableListener(table_listener, true);
        
        if (use_tcp == true)
        {
        	runTcp();
        }
        else
        {
        	runUdp();
        }
	}
	
	/**************************************************************************
	 * 
	 * The runUdp method does most of the work, it establishes the needed 
	 * connections, then loops until a problem is detected.
	 * 
	 *************************************************************************/
	private void runUdp()
	{
		System.out.println("Sending messages to " + dst_host + ":" + dst_port + ":UDP");
		
		byte[] msg_buffer = new byte[MESSAGE_BUFFER_SIZE];
		int msg_size;

		DatagramSocket send_socket = null;
		InetAddress dst_addr = null;

		try 
		{
			send_socket = new DatagramSocket();
			dst_addr = InetAddress.getByName(dst_host);
		}
		catch (Exception e) 
		{
			e.printStackTrace();
			done = true;
		}
				
		while (! done)
		{
			msg_size = buildMessage(msg_buffer);

			if (msg_size > 0)
			{
				try 
				{
					DatagramPacket pkt = new DatagramPacket(msg_buffer, msg_size, dst_addr, dst_port);
					send_socket.send(pkt);
					System.out.print("sent " + msg_size + " bytes ");
					for(int i = 0; i < msg_size; i++)
					{
						System.out.print(String.format("%02X ", msg_buffer[i]));
					}
					System.out.println("");
				} 
				catch (Exception e) 
				{
					e.printStackTrace();
					done = true;
				}
			}
			
			try 
			{
				Thread.sleep((int)(MSG_PERIOD * 1000));
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
			}
		}		
	}
	
	/**************************************************************************
	 * 
	 * The runTcp method does most of the network related work, it establishes
	 * the needed connections, then loops until a problem is detected.
	 * 
	 *************************************************************************/
	private void runTcp()
	{
		byte[] msg_buffer = new byte[MESSAGE_BUFFER_SIZE];
		int msg_size;

		Socket send_socket = null;
		DataOutputStream out_stream = null;
		
		while (! done)
		{
			if (send_socket == null)
			{
				System.out.println("Trying to connection to " + dst_host + ":" + dst_port + ":TCP");

				try 
				{
					send_socket = new Socket(dst_host, dst_port);
					
					out_stream = new DataOutputStream(send_socket.getOutputStream());
				}
				catch (Exception e) 
				{
				}

				if (send_socket == null)
				{
					try 
					{
						Thread.sleep((int)(CONNECT_PERIOD * 1000));
					} 
					catch (InterruptedException e) 
					{
						e.printStackTrace();
					}
					
					continue;
				}
			}
			
			
			msg_size = buildMessage(msg_buffer);

			if (msg_size > 0)
			{
				try 
				{
					out_stream.write(msg_buffer, 0, msg_size);
					System.out.print("sent " + msg_size + " bytes ");
					for(int i = 0; i < msg_size; i++)
					{
						System.out.print(String.format("%02X ", msg_buffer[i]));
					}
					System.out.println("");
				} 
				catch (Exception e) 
				{
					try { out_stream.close(); } catch (Exception e1) { }
					out_stream = null;

					try { send_socket.close(); } catch (Exception e1) { }
					send_socket = null;
				}
			}
			
			try 
			{
				Thread.sleep((int)(MSG_PERIOD * 1000));
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
			}
		}		
	}
	
	/**************************************************************************
	 * 
	 * Read the specified configuration file to get all needed values.
	 * 
	 * @param filename the name of the file to read
	 * 
 	 *************************************************************************/
	private void loadConfig(String filename)
	{
		try
		{
			File xml_file = new File(filename);
			
			if (! xml_file.exists())
			{
				System.out.println("Could not open configuration file: " + filename);
				return;
			}
			
			DocumentBuilder xml_builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document xml_doc = xml_builder.parse(xml_file);
			
			Element xml = xml_doc.getDocumentElement();
			NodeList nodes;
			
			nodes = xml.getElementsByTagName("comm_mode");
			if (nodes.getLength() >= 1)
			{
				Element element = (Element)(nodes.item(0));
				use_tcp = element.getAttribute("value").toLowerCase().equals("tcp");
			}
			
			nodes = xml.getElementsByTagName("dest_host");
			if (nodes.getLength() >= 1)
			{
				Element element = (Element)(nodes.item(0));
				this.dst_host = element.getAttribute("value");
			}
			
			nodes = xml.getElementsByTagName("dest_port");
			if (nodes.getLength() >= 1)
			{
				Element element = (Element)(nodes.item(0));
				this.dst_port = Integer.parseInt(element.getAttribute("value"));
			}
			
			nodes = xml.getElementsByTagName("table_host");
			if (nodes.getLength() >= 1)
			{
				Element element = (Element)(nodes.item(0));
				this.table_host = element.getAttribute("value");
			}
			
			nodes = xml.getElementsByTagName("message");
			if (nodes.getLength() != 1)
			{
				System.out.println("Invalid configuration file, file must contain exactly on message tag");
				return;
			}
			
			int message_size = MESSAGE_HEADER_SIZE;
			
			nodes = ((Element)(nodes.item(0))).getElementsByTagName("value");
			for (int i = 0; i < nodes.getLength(); i++)
			{
				Element element = (Element)(nodes.item(i));
				
				if (element.hasAttribute("name") && element.hasAttribute("type") && element.hasAttribute("default"))
				{
					String key = element.getAttribute("name");
					
					String type_str = element.getAttribute("type").toLowerCase();
					String default_value = element.getAttribute("default");
									
					if (type_str.equals("double"))
					{
						addValue(key, ValueType.DOUBLE, new Double(default_value));
						message_size += 8;
					}
					else if (type_str.equals("float"))
					{
						addValue(key, ValueType.FLOAT, new Float(default_value));
						message_size += 4;
					}
					else if (type_str.startsWith("int"))
					{
						addValue(key, ValueType.INT, new Integer(default_value));
						message_size += 4;
					}
					else if (type_str.equals("short"))
					{
						addValue(key, ValueType.SHORT, new Short(default_value));
						message_size += 2;
					}
					else if (type_str.startsWith("bool"))
					{
						addValue(key, ValueType.BOOLEAN, new Boolean(default_value));
						message_size += 1;
					}
					else
					{
						System.out.println("ERROR parsing configuration file, unsupported value type of " + type_str);
					}					     
				}
				else
				{
					System.out.println("ERROR parsing configuration file, invalid value element, missing required attribute name|type|default");
				}	
			}

			if (message_size > MESSAGE_BUFFER_SIZE)
			{
				System.out.println("ERROR message size (" + message_size +") greater than allowed (" + MESSAGE_BUFFER_SIZE + ")");
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**************************************************************************
	 *
	 * Build a Message
	 * 
	 *    start   length value
	 *    0       2      FAF3
	 *    2       2      message count, increments with each message
	 *    4       2      message size, total number of bytes
	 *    6       xx     data
	 *    
	 *************************************************************************/
	private int buildMessage(byte[] msg_buffer)
	{
		ByteBuffer bb = ByteBuffer.wrap(msg_buffer);
		
		// Put in at least a two byte sync pattern
		bb.putShort((short)0xFAF3);

		// Put in a message counter
		bb.putShort(msg_count++);

		// Save space for message length
		bb.putShort((short)0); 
		Enumeration<ValueItem> vals = value_list.elements();

		int idx = 0;
		while(vals.hasMoreElements())
		{
			ValueItem itm = vals.nextElement();
			
			switch(itm.type)
			{
				case DOUBLE:
				{
					     if ( itm.value instanceof Double ) 	bb.putDouble((double)(((Double)(itm.value)).doubleValue()));
					else if ( itm.value instanceof Float ) 		bb.putDouble((double)(((Float)(itm.value)).floatValue()));							
					else if ( itm.value instanceof Integer ) 	bb.putDouble((double)(((Integer)(itm.value)).intValue()));							
					else if ( itm.value instanceof Short ) 		bb.putDouble((double)(((Short)(itm.value)).shortValue()));							
					else if ( itm.value instanceof Boolean ) 	bb.putDouble((double)(((Boolean)(itm.value)).booleanValue()?1.0:0.0));
					else System.out.println("unsupported data conversion for index " + idx);
				} break;
				
				case FLOAT:
				{
						 if ( itm.value instanceof Float ) 		bb.putFloat((float)(((Float)(itm.value)).floatValue()));
				    else if ( itm.value instanceof Double ) 	bb.putFloat((float)(((Double)(itm.value)).doubleValue()));							
					else if ( itm.value instanceof Integer ) 	bb.putFloat((float)(((Integer)(itm.value)).intValue()));							
					else if ( itm.value instanceof Short ) 		bb.putFloat((float)(((Short)(itm.value)).shortValue()));							
					else if ( itm.value instanceof Boolean ) 	bb.putFloat((float)(((Boolean)(itm.value)).booleanValue()?1.0f:0.0f));
					else System.out.println("unsupported data conversion for index " + idx);
				} break;
			
				case INT:
				{
						 if ( itm.value instanceof Integer ) 	bb.putInt((int)(((Integer)(itm.value)).intValue()));							
					else if ( itm.value instanceof Short ) 		bb.putInt((int)(((Short)(itm.value)).shortValue()));							
					else if ( itm.value instanceof Boolean ) 	bb.putInt((int)(((Boolean)(itm.value)).booleanValue()?1:0));
					else if ( itm.value instanceof Float ) 		bb.putInt((int)(((Float)(itm.value)).floatValue()));
				    else if ( itm.value instanceof Double ) 	bb.putInt((int)(((Double)(itm.value)).doubleValue()));							
					else System.out.println("unsupported data conversion for index " + idx);
				} break;
				
				case SHORT:
				{
						 if ( itm.value instanceof Short ) 		bb.putShort((short)(((Short)(itm.value)).shortValue()));							
					else if ( itm.value instanceof Integer ) 	bb.putShort((short)(((Integer)(itm.value)).intValue()));							
					else if ( itm.value instanceof Boolean ) 	bb.putShort((short)(((Boolean)(itm.value)).booleanValue()?1:0));
					else if ( itm.value instanceof Float ) 		bb.putShort((short)(((Float)(itm.value)).floatValue()));
				    else if ( itm.value instanceof Double ) 	bb.putShort((short)(((Double)(itm.value)).doubleValue()));							
					else System.out.println("unsupported data conversion for index " + idx);
				} break;
				
				case BOOLEAN: 
				{
						 if ( itm.value instanceof Boolean ) 	bb.put((byte)(((Boolean)(itm.value)).booleanValue()?1:0));
					else if ( itm.value instanceof Short ) 		bb.put((byte)(((Short)(itm.value)).shortValue() > 0?1:0));							
					else if ( itm.value instanceof Integer ) 	bb.put((byte)(((Integer)(itm.value)).intValue() > 0?1:0));							
					else if ( itm.value instanceof Float ) 		bb.put((byte)(((Float)(itm.value)).floatValue() > 0?1:0));
				    else if ( itm.value instanceof Double ) 	bb.put((byte)(((Double)(itm.value)).doubleValue() > 0?1:0));							
					else System.out.println("unsupported data conversion for index " + idx);
				} break;
			}
		}
		
		// Put in the message length
		int size = bb.position();
		bb.putShort(4, (short)size);
		
		return size;
	}
	
	/**************************************************************************
	 * 
	 * Add a name-pair value that should be included in the messages, a default
	 * value is given in case the pair is never received from the network table.
	 * 
	 * @param key			the name of the value as it will be in the network table
	 * @param type			the type of the value
	 * @param default_value a default value
	 * 
	 *************************************************************************/
	public void addValue(String key, ValueType type, Object default_value)
	{
		System.out.println("adding " + key);
		
		if (value_map.containsKey(key))
		{
			System.out.println("Error: duplicate key - " + key);
			return;
		}
		
		ValueItem itm = new ValueItem();
		itm.type = type;
		itm.value = default_value;

		int idx = value_list.size();
		value_list.addElement(itm);
		
		value_map.put(key, idx);
	}
	
	/**************************************************************************
	 * 
	 * Set the value for a name-value pair. If the name was not previously 
	 * added with addValue(), this value is ignored.
	 * 
	 * @param key	the name of the value
	 * @param value the value
	 * 
	 **************************************************************************/
    public void setValue(String key, Object value) 
    {
		if (value_map.containsKey(key))
		{

	    	int idx = value_map.get(key);
			ValueItem itm = value_list.get(idx);
			itm.value = value;

//			System.out.println("got value: " + key + " [" + idx + "]  = " + value);
		}
    }
}

/******************************************************************************
 * 
 * This class listens for changed to the SmartDashboard ITable, any changes 
 * are reported to the DashboardToUdp application via the setValue method.
 * 
 * @reference This was originally copied from the wpilib SmartDashboard DashboardPanel.java file
 * 
 *****************************************************************************/
class MyTableListener implements ITableListener 
{
	private NetTableToSocket my_app;
	
	public MyTableListener(NetTableToSocket app)
	{
		my_app = app;
	}
	
    @Override
    public void valueChanged(final ITable source, final String key, final Object value, final boolean isNew) 
    {
    	if (value instanceof ITable) 
        {
            final ITable table = (ITable) value;
            table.addTableListenerEx
            (
            	"~TYPE~", 
        		new ITableListener() 
        		{
	                public void valueChanged(final ITable typeSource, final String typeKey, final Object typeValue, final boolean typeIsNew) 
	                {
	                    table.removeTableListener(this);
	                    SwingUtilities.invokeLater
	                    (
	                    	new Runnable() 
		                    {
		                        public void run() 
		                        {
		                        	my_app.setValue(key, value);
		                        }
		                    }
	                    );
	                }
        		}, 
	            ITable.NOTIFY_IMMEDIATE | ITable.NOTIFY_LOCAL | ITable.NOTIFY_NEW | ITable.NOTIFY_UPDATE
	        );
        } 
        else 
        {
            SwingUtilities.invokeLater
            (
            	new Runnable() 
	            {
	                public void run() 
	                {
	                	my_app.setValue(key, value);
	                }
	            }
            );
        } 
    }
}

