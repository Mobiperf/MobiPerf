/**
 * @author Junxian Huang
 * @date Aug 29, 2009
 * @time 5:10:25 PM
 * @organization University of Michigan, Ann Arbor
 */
package servers;

import java.io.IOException;
import java.io.OutputStreamWriter;

import common.BaseTcpWorker;
import common.Definition;
import common.Util;

/**
 * @author Junxian Huang
 *
 */
public class DownlinkWorker extends BaseTcpWorker {

	public long id;
	public int port;

	public DownlinkWorker(int port){
		this.port = port;
	}

	public void run() {

		try {
			client.setSoTimeout(Definition.TCP_RECEIVE_TIMEOUT);
			OutputStreamWriter out = new OutputStreamWriter(client.getOutputStream());

			long start = System.currentTimeMillis();
			long end = start;
			int batch = 0;

			while (end - start < Definition.THROUGHPUT_DURATION) {
				out.write(Util.genRandomString(Definition.THROUGHPUT_DOWN_SEGMENT_SIZE)); //2600 larger than MTU
				out.flush();
				batch++;
				if(batch % 50 == 0){
					end = System.currentTimeMillis();
				}
			}

			out.close();
			client.close();

			System.out.println("Downlink worker <" + id + "> Thread ends");

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
